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
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.mfa.application.dto.ResultadoCambioMfa;
import com.marketplace.auth_service.mfa.application.port.in.CambioMfaPort;
import com.marketplace.auth_service.sesion.adapter.out.persistence.SesionJpaAdapter;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** Cuenta, familia, OTP, auditoría y outbox reales sobre PostgreSQL desechable; no envía correo. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate"}, showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({CambioMfaPostgresTest.Config.class, UsuarioConfiguration.class, AutorizacionConfig.class,
        SesionConfig.class, SesionJpaAdapter.class, IdentidadConfig.class, MfaConfig.class, MfaCriptografiaConfig.class,
        CredencialesConfig.class, CredencialesJpaAdapter.class, MfaJpaAdapter.class,
        CambioMfaConfig.class, CambioMfaJdbcAdapter.class, AuditoriaJpaAdapter.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Testcontainers
class CambioMfaPostgresTest {
    static class Reloj extends Clock {
        volatile Instant ahora=Instant.parse("2026-10-20T17:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId z){return this;}
        public Instant instant(){return ahora;}
    }
    @TestConfiguration(proxyBeanMethods=false)
    @EntityScan("com.marketplace.auth_service")
    @EnableJpaRepositories("com.marketplace.auth_service")
    static class Config {
        @Bean Reloj reloj(){return new Reloj();}
        @Bean CifradoMensajesPort cifrado(){return new CifradoMensajesAesGcm("test-mfa-v2",new byte[32]);}
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
        try(var c=DriverManager.getConnection(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()); var s=c.createStatement()) {
            try(var in=new ClassPathResource("sesion/base-observada/schema.sql").getInputStream()) {
                s.execute(new String(in.readAllBytes(),StandardCharsets.UTF_8));
            }
            c.setAutoCommit(false);
            try(var archivos=Files.list(Path.of("src/main/resources/db/migration"))) {
                for(var p:archivos.filter(p->p.toString().endsWith(".sql")).sorted().toList()) s.execute(Files.readString(p));
            }
            s.execute("insert into rol(codigo,nombre) values('CLIENTE','Cliente'),('ADMIN_SISTEMA','Administrador')"); c.commit();
        }
        Path comunes=Path.of("build/test-fixtures/mfa-v2-comunes.txt").toAbsolutePath();
        Files.createDirectories(comunes.getParent());Files.writeString(comunes,"ComunDePrueba8!\n");
        r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);r.add("g7.password.comunes.archivo",comunes::toString);
        r.add("AUTH_OTP_HMAC_KEY_ID",()->"test-mfa-v2"); r.add("AUTH_OTP_HMAC_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
        r.add("identidad.issuer",()->"https://mfa-v2.example.test/auth");
        r.add("identidad.clave-privada",()->ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }
    @Autowired CambioMfaPort cambios;
    @Autowired JdbcTemplate jdbc;
    @Autowired CifradoMensajesPort cifrado;
    @Autowired Reloj reloj;
    UUID usuario, familia;
    ActorUsuario actor;
    String correo;
    ContextoPeticion contexto(){return new ContextoPeticion("127.0.0.1","test-mfa-v2",UUID.randomUUID());}
    @BeforeEach void preparar() {
        reloj.ahora=Instant.parse("2026-10-20T17:00:00Z"); usuario=UUID.randomUUID();familia=UUID.randomUUID();
        correo=usuario+"@example.test";
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado,correo_verificado) values(?,?,'Cuenta','MFA','ACTIVO',true)",usuario,correo);
        jdbc.update("insert into usuario_rol(usuario_id,rol_codigo) values(?,'CLIENTE')",usuario);
        jdbc.update("insert into sesion_familia(id,usuario_id,creado_en) values(?,?,?)",familia,usuario,Timestamp.from(reloj.instant()));
        actor=acceso(familia);
    }
    ActorUsuario acceso(UUID f) {
        UUID jti=UUID.randomUUID();
        jdbc.update("insert into acceso_sesion(jti,usuario_id,familia_id,creado_en,expira_en) values(?,?,?,?,?)",
                jti,usuario,f,Timestamp.from(reloj.instant()),Timestamp.from(reloj.instant().plusSeconds(900)));
        return new ActorUsuario(usuario,jti,Set.of("CLIENTE"),Set.of());
    }
    @AfterEach void quitarFallos() {
        jdbc.execute("alter table auditoria_seguridad drop constraint if exists mfa_fallo_auditoria");
        jdbc.execute("alter table outbox drop constraint if exists mfa_fallo_correo");
    }
    ResultadoCambioMfa.Enviado solicitar() { return (ResultadoCambioMfa.Enviado)cambios.solicitar(actor,"HABILITAR",contexto()); }
    String codigo() throws Exception {
        var filas=jdbc.queryForList("select payload::text as carga from outbox where topico='correo.cambio_mfa_otp' order by creado_en desc");
        for(var fila:filas) {
            var carga=new ObjectMapper().readTree((String)fila.get("carga"));
            if(cifrado.descifrar(carga.path("destino").asText()).equals(correo)) {
                var m=Pattern.compile("[0-9]{6}").matcher(cifrado.descifrar(carga.path("contenido").asText()));
                if(m.find()) return m.group();
            }
        }
        throw new AssertionError("No hay entrega propia en el outbox");
    }
    long contar(String tabla) {return jdbc.queryForObject("select count(*) from "+tabla+" where usuario_id=?",Long.class,usuario);}
    boolean habilitado(){return jdbc.queryForObject("select mfa_habilitado from usuario where id=?",Boolean.class,usuario);}
    @Test void confirmacionPersisteBanderaConsumoAuditoriaYCorreoCifradosSinNuevaSesion() throws Exception {
        var e=solicitar();String codigo=codigo();assertThat(habilitado()).isFalse();
        assertThat(jdbc.queryForObject("select hash_token from cambio_mfa where usuario_id=?",String.class,usuario)).isNotEqualTo(e.mfaChangeToken());
        assertThat(cambios.confirmar(actor,e.mfaChangeToken(),codigo,contexto())).isEqualTo(ResultadoCambioMfa.Confirmado.EXITO);
        assertThat(habilitado()).isTrue();assertThat(contar("sesion_familia")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from auditoria_seguridad where objetivo_usuario_id=? and accion='MFA_ACTIVADO'",Long.class,usuario)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select consumido from cambio_mfa where usuario_id=?",Boolean.class,usuario)).isTrue();
        assertThat(jdbc.queryForObject("select hash_codigo from otp where usuario_id=?",String.class,usuario)).hasSize(64).isNotEqualTo(codigo);
    }
    @Test void fallaAuditoriaRevierteConsumoBanderaYAviso() throws Exception {
        var e=solicitar();String codigo=codigo();
        jdbc.execute("alter table auditoria_seguridad add constraint mfa_fallo_auditoria check(accion <> 'MFA_ACTIVADO') not valid");
        assertThatThrownBy(()->cambios.confirmar(actor,e.mfaChangeToken(),codigo,contexto())).isInstanceOf(RuntimeException.class);
        assertThat(habilitado()).isFalse();
        assertThat(jdbc.queryForObject("select consumido from cambio_mfa where usuario_id=?",Boolean.class,usuario)).isFalse();
        assertThat(jdbc.queryForObject("select usado from otp where usuario_id=?",Boolean.class,usuario)).isFalse();
    }
    @Test void fallaOutboxRevierteSolicitudYCuota() {
        jdbc.execute("alter table outbox add constraint mfa_fallo_correo check(topico <> 'correo.cambio_mfa_otp') not valid");
        assertThatThrownBy(this::solicitar).isInstanceOf(RuntimeException.class);
        assertThat(contar("cambio_mfa")).isZero();assertThat(contar("otp")).isZero();
    }
    @Test void dosConfirmacionesConcurrentesSoloProducenUnaTransicion() throws Exception {
        var e=solicitar();String codigo=codigo();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var inicio=new CountDownLatch(1);
            Callable<ResultadoCambioMfa> tarea=()->{inicio.await();return cambios.confirmar(actor,e.mfaChangeToken(),codigo,contexto());};
            var a=pool.submit(tarea);var b=pool.submit(tarea);inicio.countDown();
            var resultados=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(r->r==ResultadoCambioMfa.Confirmado.EXITO).count()).isEqualTo(1);
            assertThat(resultados.stream().filter(r->r instanceof ResultadoCambioMfa.Error error && error.code().equals("TOKEN_INVALIDO")).count()).isEqualTo(1);
        }
    }
    @Test void codigoIncorrectoConfirmaSuIntentoYFamiliaRevocadaDevuelveRechazoSinErrorDeCommit() throws Exception {
        var e=solicitar();String incorrecto=codigo().equals("000000")?"000001":"000000";
        assertThat(((ResultadoCambioMfa.Error)cambios.confirmar(actor,e.mfaChangeToken(),incorrecto,contexto())).code()).isEqualTo("CODIGO_INVALIDO");
        assertThat(jdbc.queryForObject("select intentos from otp where usuario_id=?",Integer.class,usuario)).isEqualTo(1);
        jdbc.update("update sesion_familia set revocada=true,revocada_en=creado_en where id=?",familia);
        assertThat(((ResultadoCambioMfa.Error)cambios.confirmar(actor,e.mfaChangeToken(),codigo(),contexto())).code()).isEqualTo("TOKEN_INVALIDO");
    }
    @Test void nuevaSolicitudReemplazaSoloOperacionPreviaYRespetaFkUsuarioFamilia() {
        var anterior=solicitar();var nuevo=solicitar();
        assertThat(contar("cambio_mfa")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from cambio_mfa where usuario_id=? and invalidado_en is null",Long.class,usuario)).isEqualTo(1);
        assertThat(((ResultadoCambioMfa.Error)cambios.confirmar(actor,anterior.mfaChangeToken(),"000000",contexto())).code()).isEqualTo("TOKEN_INVALIDO");
        assertThat(nuevo.mfaChangeToken()).isNotEqualTo(anterior.mfaChangeToken());
    }
    @Test void otroJtiMismaFamiliaConfirmaYOtraFamiliaNo() throws Exception {
        var e=solicitar();String codigo=codigo();
        UUID otra=UUID.randomUUID();jdbc.update("insert into sesion_familia(id,usuario_id) values(?,?)",otra,usuario);
        assertThat(((ResultadoCambioMfa.Error)cambios.confirmar(acceso(otra),e.mfaChangeToken(),codigo,contexto())).code()).isEqualTo("TOKEN_INVALIDO");
        assertThat(cambios.confirmar(acceso(familia),e.mfaChangeToken(),codigo,contexto())).isEqualTo(ResultadoCambioMfa.Confirmado.EXITO);
    }
    @Test void avisoFallidoRevierteEstadoConsumoYAuditoriaCritica() throws Exception {
        var e=solicitar();String codigo=codigo();
        jdbc.execute("alter table outbox add constraint mfa_fallo_correo check(topico <> 'correo.cambio_mfa_confirmado') not valid");
        assertThatThrownBy(()->cambios.confirmar(actor,e.mfaChangeToken(),codigo,contexto())).isInstanceOf(RuntimeException.class);
        assertThat(habilitado()).isFalse();
        assertThat(jdbc.queryForObject("select consumido from cambio_mfa where usuario_id=?",Boolean.class,usuario)).isFalse();
        assertThat(jdbc.queryForObject("select usado from otp where usuario_id=?",Boolean.class,usuario)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from auditoria_seguridad where objetivo_usuario_id=? and accion='MFA_ACTIVADO'",Long.class,usuario)).isZero();
    }
    @Test void reenviarPersisteCodigoNuevoSinExtenderCambioYSuVencimientoPrecedeAlOtp() {
        var e=solicitar();
        UUID anterior=jdbc.queryForObject("select otp_actual_id from cambio_mfa where usuario_id=?",UUID.class,usuario);
        reloj.ahora=reloj.ahora.plusSeconds(360);
        var r=(ResultadoCambioMfa.Enviado)cambios.reenviar(actor,e.mfaChangeToken(),contexto());
        assertThat(r.cambioExpiraEn()).isEqualTo(240);assertThat(r.expiraEn()).isEqualTo(300);
        assertThat(jdbc.queryForObject("select otp_actual_id from cambio_mfa where usuario_id=?",UUID.class,usuario)).isNotEqualTo(anterior);
        assertThat(jdbc.queryForObject("select invalidado_en from otp where id=?",Timestamp.class,anterior)).isNotNull();
        reloj.ahora=reloj.ahora.plusSeconds(240);
        assertThat(((ResultadoCambioMfa.Error)cambios.confirmar(actor,e.mfaChangeToken(),"000000",contexto())).code()).isEqualTo("TOKEN_INVALIDO");
    }
}
