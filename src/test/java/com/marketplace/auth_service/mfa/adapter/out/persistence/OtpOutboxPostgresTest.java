package com.marketplace.auth_service.mfa.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.auditoria.adapter.in.transaction.AuditoriaTransaccional;
import com.marketplace.auth_service.auditoria.adapter.out.correo.EnvioCorreoSmtp;
import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesAesGcm;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.*;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.repository.OutboxJpaRepository;
import com.marketplace.auth_service.auditoria.adapter.out.transaccion.TareasTrasCommitSpring;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.out.*;
import com.marketplace.auth_service.auditoria.application.service.*;
import com.marketplace.auth_service.configuration.*;
import com.marketplace.auth_service.credencial.adapter.out.persistence.CredencialesJpaAdapter;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.mfa.application.dto.*;
import com.marketplace.auth_service.mfa.application.port.in.*;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** Wiring y proveedores productivos; SMTP ausente falla realmente, sin transporte ficticio. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({OtpOutboxPostgresTest.Config.class,MfaConfig.class,MfaCriptografiaConfig.class,
        CredencialesConfig.class,CredencialesJpaAdapter.class,UsuarioConfiguration.class,
        AutorizacionConfig.class,MfaJpaAdapter.class,AuditoriaJpaAdapter.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Testcontainers
class OtpOutboxPostgresTest {
    static final class Reloj extends Clock {
        volatile Instant ahora=Instant.now();
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(ahora,zone);}
        @Override public Instant instant(){return ahora;}
        void avanzar(long segundos){ahora=ahora.plusSeconds(segundos);}
    }
    @TestConfiguration(proxyBeanMethods=false)
    @EntityScan({"com.marketplace.auth_service.mfa.adapter.out.persistence.entity",
            "com.marketplace.auth_service.credencial.adapter.out.persistence.entity",
            "com.marketplace.auth_service.usuario.adapter.out.persistence.entity",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.entity"})
    @EnableJpaRepositories({"com.marketplace.auth_service.mfa.adapter.out.persistence.repository",
            "com.marketplace.auth_service.credencial.adapter.out.persistence.repository",
            "com.marketplace.auth_service.usuario.adapter.out.persistence.repository",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.repository"})
    static class Config {
        @Bean Reloj reloj(){return new Reloj();}
        @Bean CifradoMensajesPort cifrado(){return new CifradoMensajesAesGcm("test-otp",new byte[32]);}
        @Bean OutboxPort outbox(OutboxJpaRepository filas){return new OutboxJpaAdapter(filas);}
        @Bean AuditoriaPort auditoria(AuditoriaPersistenciaPort filas,PlatformTransactionManager tx){
            return new AuditoriaTransaccional(new AuditoriaService(filas,new TareasTrasCommitSpring(tx)));
        }
        @Bean MensajeriaService mensajeria(OutboxPort outbox,CifradoMensajesPort cifrado,Clock reloj){
            return new MensajeriaService(outbox,cifrado,new EnvioCorreoSmtp(null,"test@example.test"),reloj);
        }
    }
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) throws Exception {
        PG.start();
        try(var c=DriverManager.getConnection(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword());var s=c.createStatement()){
            try(var in=new ClassPathResource("sesion/base-observada/schema.sql").getInputStream()){
                s.execute(new String(in.readAllBytes(),StandardCharsets.UTF_8));
            }
            c.setAutoCommit(false);
            try(var migraciones=Files.list(Path.of("src/main/resources/db/migration"))){
                for(var p:migraciones.filter(p->p.toString().endsWith(".sql")).sorted().toList()) s.execute(Files.readString(p));
            }
            c.commit();s.execute("insert into rol(codigo,nombre) values('CLIENTE','Cliente')");c.commit();
        }
        Path comunes=Path.of("build/test-fixtures/otp-comunes.txt").toAbsolutePath();
        Files.createDirectories(comunes.getParent());Files.writeString(comunes,"ComunDePrueba8!\n");
        r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);r.add("g7.password.comunes.archivo",comunes::toString);
        r.add("AUTH_OTP_HMAC_KEY_ID",()->"test-otp");
        r.add("AUTH_OTP_HMAC_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    @Autowired OtpLoginPort otp;
    @Autowired MfaPort mfa;
    @Autowired CuentasPort cuentas;
    @Autowired PasswordHasherPort hasher;
    @Autowired PersistenciaMfaPort persistencia;
    @Autowired CriptografiaMfaPort crypto;
    @Autowired CifradoMensajesPort cifrado;
    @Autowired MensajeriaService mensajeria;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired Reloj reloj;
    UUID usuario;
    String correo,token;
    @BeforeEach void preparar(){
        // PG timestamptz conserva microsegundos; evitar redondear creado_en al futuro
        // frente a un reloj de prueba congelado con precisión de nanosegundos.
        reloj.ahora=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        usuario=UUID.randomUUID();correo=usuario+"@example.test";
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado,correo_verificado,mfa_habilitado) values(?,?,'Persona','Prueba','ACTIVO',true,true)",usuario,correo);
        jdbc.update("insert into usuario_rol(usuario_id,rol_codigo) values(?,'CLIENTE')",usuario);
        jdbc.update("insert into credencial(usuario_id,hash_contrasena,version) values(?,?,0)",usuario,hasher.generar("Clave-Segura-2026!").valor());
        token=new TransactionTemplate(manager).execute(s->{cuentas.bloquear(usuario);
            return ((ResultadoCreacionDesafio.Creado)mfa.crearDesafio(usuario,new RevisionCredencialMfa(usuario,usuario,0)))
                    .desafio().challengeToken();});
    }
    @AfterEach void retirarFallo(){jdbc.execute("alter table outbox drop constraint if exists otp_fallo");}
    ResultadoSolicitudOtp solicitar(){return otp.solicitar(token,CanalMfa.EMAIL);}
    long codigos(){return jdbc.queryForObject("select count(*) from otp where usuario_id=?",Long.class,usuario);}
    List<Map<String,Object>> correos(){
        return jdbc.queryForList("select id,estado,intentos,payload::text as carga from outbox where topico='correo.otp'")
                .stream().filter(f->{try{return cifrado.descifrar(new ObjectMapper().readTree((String)f.get("carga")).path("destino").asText()).equals(correo);}
                catch(Exception e){throw new IllegalStateException(e);}}).toList();
    }
    @Test void solicitudSinSmtpConfirmaOtpHmacYCorreoCifradoEnMismoCommit() throws Exception {
        assertThat(solicitar()).isInstanceOf(ResultadoSolicitudOtp.Enviado.class);
        assertThat(codigos()).isEqualTo(1);assertThat(correos()).hasSize(1);
        var fila=correos().getFirst();assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
        String carga=(String)fila.get("carga");assertThat(carga).doesNotContain(correo,"Tu código");
        String cuerpo=cifrado.descifrar(new ObjectMapper().readTree(carga).path("contenido").asText());
        var matcher=java.util.regex.Pattern.compile("\\b[0-9]{6}\\b").matcher(cuerpo);assertThat(matcher.find()).isTrue();
        String codigo=matcher.group();assertThat(carga).doesNotContain(codigo);
        var desafio=persistencia.buscarDesafioPorHash(crypto.hashDesafio(token)).orElseThrow();
        var guardado=persistencia.listarCodigosDelDesafio(desafio.id()).getFirst();
        assertThat(crypto.coincideOtp(guardado.contexto(),codigo,guardado.hashCodigo(),guardado.hashKeyId())).isTrue();
    }
    @Test void falloDeEncoladoRevierteReenvioCuotaEInvalidacionAnterior(){
        solicitar();reloj.avanzar(1);
        jdbc.execute("alter table outbox add constraint otp_fallo check(topico <> 'correo.otp') not valid");
        assertThatThrownBy(this::solicitar).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(codigos()).isEqualTo(1);assertThat(correos()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from otp where usuario_id=? and invalidado_en is null",Long.class,usuario)).isEqualTo(1);
    }
    @Test void reenvioInvalidaAnteriorRespetaCuotaYNoExtiendeDesafio(){
        solicitar();reloj.avanzar(100);
        assertThat(((ResultadoSolicitudOtp.Enviado)solicitar()).desafioExpiraEn()).isBetween(199,200);
        reloj.avanzar(1);solicitar();
        assertThat(solicitar()).isInstanceOf(ResultadoSolicitudOtp.Limite.class);
        assertThat(codigos()).isEqualTo(3);assertThat(correos()).hasSize(3);
        assertThat(jdbc.queryForObject("select count(*) from otp where usuario_id=? and invalidado_en is not null",Long.class,usuario)).isEqualTo(2);
    }
    @Test void desafioVencidoNoEncolaNiConsumeCuota(){
        reloj.avanzar(301);assertThat(solicitar()).isEqualTo(ResultadoSolicitudOtp.Rechazo.DESAFIO_INVALIDO);
        assertThat(codigos()).isZero();assertThat(correos()).isEmpty();
    }
    @Test void falloYReintentoSmtpConservanMismoCorreoSinGenerarOtpNiConsumirCuota(){
        solicitar();reloj.ahora=Instant.now().plusSeconds(5);
        assertThat(mensajeria.procesarCorreos()).isZero();
        var primero=correos().getFirst();assertThat(primero.get("intentos")).isEqualTo(1);
        jdbc.update("update outbox set proximo_intento_en=? where id=?",OffsetDateTime.ofInstant(reloj.instant().minusSeconds(1),ZoneOffset.UTC),primero.get("id"));
        assertThat(mensajeria.procesarCorreos()).isZero();
        assertThat(correos()).singleElement().satisfies(f->{
            assertThat(f.get("id")).isEqualTo(primero.get("id"));assertThat(f.get("intentos")).isEqualTo(2);
            assertThat(f.get("carga")).isEqualTo(primero.get("carga"));
        });assertThat(codigos()).isEqualTo(1);
    }
    @Test void solicitudesConcurrentesEnLimiteSoloEncolanUnaMas() throws Exception {
        solicitar();reloj.avanzar(1);solicitar();reloj.avanzar(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var inicio=new CountDownLatch(1);
            var a=pool.submit(()->{inicio.await();return solicitar();});
            var b=pool.submit(()->{inicio.await();return solicitar();});inicio.countDown();
            var resultados=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(ResultadoSolicitudOtp.Enviado.class::isInstance).count()).isEqualTo(1);
            assertThat(resultados.stream().filter(ResultadoSolicitudOtp.Limite.class::isInstance).count()).isEqualTo(1);
        }
        assertThat(codigos()).isEqualTo(3);assertThat(correos()).hasSize(3);
    }
}
