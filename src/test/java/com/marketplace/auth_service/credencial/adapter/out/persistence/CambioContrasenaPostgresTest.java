package com.marketplace.auth_service.credencial.adapter.out.persistence;

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
import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.credencial.application.port.in.*;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.sesion.adapter.out.persistence.SesionJpaAdapter;
import com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter;
import com.marketplace.auth_service.sesion.application.dto.*;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
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

/** Proveedores reales, sin worker programado ni correo enviado. Fallos SQL inducidos
 * únicamente en el PostgreSQL desechable prueban el rollback del commit completo. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({CambioContrasenaPostgresTest.Config.class, CambioContrasenaConfig.class,
        CredencialesConfig.class,CredencialesJpaAdapter.class,UsuarioConfiguration.class,
        AutorizacionConfig.class,SesionConfig.class,SesionJpaAdapter.class,IdentidadConfig.class,AuditoriaJpaAdapter.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Testcontainers
class CambioContrasenaPostgresTest {
    @TestConfiguration(proxyBeanMethods=false)
    @EntityScan(basePackages={"com.marketplace.auth_service.credencial.adapter.out.persistence.entity",
            "com.marketplace.auth_service.usuario.adapter.out.persistence.entity",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.sesion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.entity"})
    @EnableJpaRepositories(basePackages={"com.marketplace.auth_service.credencial.adapter.out.persistence.repository",
            "com.marketplace.auth_service.usuario.adapter.out.persistence.repository",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.sesion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.repository"})
    static class Config {
        @Bean Clock reloj(){return Clock.systemUTC();}
        @Bean CifradoMensajesPort cifrado(){return new CifradoMensajesAesGcm("test-t008",new byte[32]);}
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
            c.commit();
            s.execute("insert into rol(codigo,nombre) values('CLIENTE','Cliente')");c.commit();
        }
        Path comunes=Path.of("build/test-fixtures/t008-comunes.txt").toAbsolutePath();
        Files.createDirectories(comunes.getParent());Files.writeString(comunes,"ComunDePrueba8!\n");
        r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);r.add("g7.password.comunes.archivo",comunes::toString);
        r.add("identidad.issuer",()->"https://t008.example.test/auth");
        r.add("identidad.clave-privada",()->ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }
    @Autowired CambioContrasenaPort cambio;
    @Autowired SesionesPort sesiones;
    @Autowired CuentasPort cuentas;
    @Autowired PasswordHasherPort hasher;
    @Autowired CifradoMensajesPort cifrado;
    @Autowired MensajeriaService mensajeria;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    static final String ACTUAL="Actual-Segura-2026!", NUEVA="Otra-Clave-2026!";
    UUID usuario,jti,familiaActual,otraFamilia;
    String hashOriginal,correo;
    ContextoPeticion contexto(){return new ContextoPeticion("127.0.0.1","t008",UUID.randomUUID());}
    @BeforeEach void preparar(){
        usuario=UUID.randomUUID();correo=usuario+"@example.test";
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado) values(?,?,'Persona','Prueba','ACTIVO')",usuario,correo);
        jdbc.update("insert into usuario_rol(usuario_id,rol_codigo) values(?,'CLIENTE')",usuario);
        hashOriginal=hasher.generar(ACTUAL).valor();
        jdbc.update("insert into credencial(usuario_id,hash_contrasena,ultimo_cambio,version) values(?,?,now(),0)",usuario,hashOriginal);
        var primera=abrir();var otra=abrir();
        familiaActual=familia(primera.refreshToken());otraFamilia=familia(otra.refreshToken());
        jti=jdbc.queryForObject("select jti from acceso_sesion where familia_id=?",UUID.class,familiaActual);
    }
    @AfterEach void retirarFallosInducidos(){
        jdbc.execute("alter table outbox drop constraint if exists t008_fallo");
        jdbc.execute("alter table auditoria_seguridad drop constraint if exists t008_fallo");
    }
    SesionIniciada abrir(){return new TransactionTemplate(manager).execute(s->{
        cuentas.bloquear(usuario);return sesiones.abrir(usuario,new IdentidadAutorizada(usuario,"Persona Prueba",correo,
                List.of("CLIENTE"),List.of()),contexto());});}
    UUID familia(String secreto){return jdbc.queryForObject("select familia_id from token_refresco where hash_token=?",UUID.class,
            SesionCryptoAdapter.sha256Hex(secreto));}
    ResultadoCambioContrasena cambiar(String actual,String nueva){return cambio.cambiar(usuario,jti,new SecretoEfimero(actual),
            new SecretoEfimero(nueva),contexto());}
    boolean revocada(UUID f){return jdbc.queryForObject("select revocada from sesion_familia where id=?",Boolean.class,f);}
    long contar(String tabla){return jdbc.queryForObject("select count(*) from "+tabla+" where usuario_id=?",Long.class,usuario);}
    long auditorias(){return jdbc.queryForObject("select count(*) from auditoria_seguridad where objetivo_usuario_id=?",Long.class,usuario);}
    List<Map<String,Object>> correos(){
        return jdbc.queryForList("select id,estado,intentos,payload::text as carga from outbox where topico='correo.contrasena_cambiada'")
                .stream().filter(f->{try{return cifrado.descifrar(new ObjectMapper().readTree((String)f.get("carga")).path("destino").asText()).equals(correo);}
                catch(Exception e){throw new IllegalStateException(e);}}).toList();
    }
    void sinEfectos(){
        assertThat(jdbc.queryForObject("select hash_contrasena from credencial where usuario_id=?",String.class,usuario)).isEqualTo(hashOriginal);
        assertThat(jdbc.queryForObject("select version from credencial where usuario_id=?",Long.class,usuario)).isZero();
        assertThat(contar("password_historial")).isZero();assertThat(auditorias()).isZero();assertThat(correos()).isEmpty();
        assertThat(revocada(otraFamilia)).isFalse();
    }
    @Test void commitIncluyeHashHistorialAuditoriaCorreoCifradoYSoloRevocaOtras() throws Exception {
        assertThat(cambiar(ACTUAL,NUEVA)).isEqualTo(ResultadoCambioContrasena.Simple.CAMBIADA);
        assertThat(contar("password_historial")).isEqualTo(1);assertThat(auditorias()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select version from credencial where usuario_id=?",Long.class,usuario)).isEqualTo(1);
        assertThat(revocada(familiaActual)).isFalse();assertThat(revocada(otraFamilia)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where familia_id=? and revocado",Long.class,familiaActual)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where familia_id=? and not revocado",Long.class,otraFamilia)).isZero();
        var aviso=correos();assertThat(aviso).hasSize(1);assertThat(aviso.getFirst().get("estado")).isEqualTo("PENDIENTE");
        String carga=(String)aviso.getFirst().get("carga");assertThat(carga).doesNotContain(correo,ACTUAL,NUEVA);
        var cuerpo=cifrado.descifrar(new ObjectMapper().readTree(carga).path("contenido").asText());
        assertThat(cuerpo).contains("fue cambiada").doesNotContain(ACTUAL,NUEVA);
    }
    @Test void falloDeOutboxRevierteTambienCredencialHistorialAuditoriaYRevocaciones(){
        jdbc.execute("alter table outbox add constraint t008_fallo check(topico <> 'correo.contrasena_cambiada') not valid");
        assertThatThrownBy(()->cambiar(ACTUAL,NUEVA)).isInstanceOf(DataIntegrityViolationException.class);
        sinEfectos();assertThat(revocada(familiaActual)).isFalse();
    }
    @Test void falloDeAuditoriaRevierteHashHistorialFamiliasYSinCorreo(){
        jdbc.execute("alter table auditoria_seguridad add constraint t008_fallo check(accion <> 'CONTRASENA_CAMBIADA') not valid");
        assertThatThrownBy(()->cambiar(ACTUAL,NUEVA)).isInstanceOf(DataIntegrityViolationException.class);
        sinEfectos();assertThat(revocada(familiaActual)).isFalse();
    }
    @Test void actualIncorrectaONuevaInvalidaNoCambianNiCierranSesion(){
        assertThat(cambiar("incorrecta",NUEVA)).isEqualTo(ResultadoCambioContrasena.Simple.ACTUAL_INCORRECTA);
        assertThat(cambiar(ACTUAL,"corta")).isInstanceOf(ResultadoCambioContrasena.PoliticaIncumplida.class);
        sinEfectos();assertThat(revocada(familiaActual)).isFalse();
    }
    @Test void accesoSinFamiliaNoCambiaNiRevoca(){
        jti=UUID.randomUUID();assertThat(cambiar(ACTUAL,NUEVA)).isEqualTo(ResultadoCambioContrasena.Simple.SESION_INVALIDA);
        sinEfectos();assertThat(revocada(familiaActual)).isFalse();
    }
    @Test void familiaActualRevocadaNoCambiaNiRevocaLaOtra(){
        // La sesión usa el reloj Java: el fixture no debe depender de su desfase con PostgreSQL.
        jdbc.update("update sesion_familia set revocada=true,revocada_en=greatest(creado_en,clock_timestamp()) where id=?",familiaActual);
        assertThat(cambiar(ACTUAL,NUEVA)).isEqualTo(ResultadoCambioContrasena.Simple.SESION_INVALIDA);sinEfectos();
    }
    @Test void falloPosteriorDeSmtpDejaAvisoDurableYNoRevierteCambio(){
        assertThat(cambiar(ACTUAL,NUEVA)).isEqualTo(ResultadoCambioContrasena.Simple.CAMBIADA);
        assertThat(mensajeria.procesarCorreos()).isZero();
        assertThat(correos()).hasSize(1);assertThat((Number)correos().getFirst().get("intentos")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select version from credencial where usuario_id=?",Long.class,usuario)).isEqualTo(1);
        assertThat(revocada(familiaActual)).isFalse();assertThat(revocada(otraFamilia)).isTrue();
    }
    @Test void dosCambiosConcurrentesSoloConfirmanUnaRevisionYUnCorreo() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)){
            var inicio=new CountDownLatch(1);
            var a=pool.submit(()->{inicio.await();return cambiar(ACTUAL,NUEVA);});
            var b=pool.submit(()->{inicio.await();return cambiar(ACTUAL,"Diferente-Clave-2026!");});
            inicio.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(ResultadoCambioContrasena.Simple.CAMBIADA,ResultadoCambioContrasena.Simple.ACTUAL_INCORRECTA);
        }
        assertThat(contar("password_historial")).isEqualTo(1);assertThat(auditorias()).isEqualTo(1);assertThat(correos()).hasSize(1);
    }
}
