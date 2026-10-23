package com.marketplace.auth_service.mfa.adapter.in.transaction;

import com.marketplace.auth_service.configuration.*;
import com.marketplace.auth_service.credencial.adapter.out.persistence.CredencialesJpaAdapter;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.mfa.adapter.out.persistence.MfaJpaAdapter;
import com.marketplace.auth_service.mfa.application.dto.*;
import com.marketplace.auth_service.mfa.application.port.in.MfaPort;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.mfa.application.service.MfaService;
import com.marketplace.auth_service.mfa.application.service.ValidadorDesafioLogin;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.marketplace.auth_service.shared.domain.Rol.*;
import static org.assertj.core.api.Assertions.*;

/** Proveedores productivos de MFA, cuentas, roles y credenciales; solo datos/claves sintéticos. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({MfaTransaccionalPostgresTest.PersistenciaPropia.class,MfaConfig.class,MfaCriptografiaConfig.class,
        UsuarioConfiguration.class,AutorizacionConfig.class,CredencialesConfig.class,CredencialesJpaAdapter.class,MfaJpaAdapter.class})
@Testcontainers
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class MfaTransaccionalPostgresTest {
    @TestConfiguration(proxyBeanMethods=false)
    @EntityScan({"com.marketplace.auth_service.mfa.adapter.out.persistence.entity",
            "com.marketplace.auth_service.usuario.adapter.out.persistence.entity",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.credencial.adapter.out.persistence.entity"})
    @EnableJpaRepositories({"com.marketplace.auth_service.mfa.adapter.out.persistence.repository",
            "com.marketplace.auth_service.usuario.adapter.out.persistence.repository",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.credencial.adapter.out.persistence.repository"})
    static class PersistenciaPropia {
        @Bean Clock relojMfaPrueba() { return Clock.fixed(AHORA,ZoneOffset.UTC); }
    }
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    private static final Instant AHORA=Instant.parse("2026-10-14T05:00:00Z");
    @DynamicPropertySource static void base(DynamicPropertyRegistry r) {
        POSTGRES.start();
        try(var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
            var entrada=new ClassPathResource("mfa/base-observada/schema.sql").getInputStream();var s=c.createStatement()) {
            s.execute(new String(entrada.readAllBytes(),StandardCharsets.UTF_8));
            c.setAutoCommit(false);
            for(String sql:List.of("V101__credencial_version.sql","V102__sesion_familia_acceso.sql", "V103__mfa_desafio_otp.sql","V105__token_un_uso_contexto_id.sql","V108__cambio_mfa.sql"))
                s.execute(java.nio.file.Files.readString(java.nio.file.Path.of("docs/migraciones-propuestas/"+sql)));
            c.commit();
        } catch(Exception e) { throw new IllegalStateException("Fixture observada MFA no disponible",e); }
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username",POSTGRES::getUsername);
        r.add("spring.datasource.password",POSTGRES::getPassword);
        r.add("AUTH_OTP_HMAC_KEY_ID",()->"prueba-mfa");
        r.add("AUTH_OTP_HMAC_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    @Autowired MfaPort mfa;
    @Autowired CuentasPort cuentas;
    @Autowired CredencialesPort credenciales;
    @Autowired PasswordHasherPort hasher;
    @Autowired CriptografiaMfaPort crypto;
    @Autowired PersistenciaMfaPort almacenamiento;
    @Autowired CuentasMfaPort cuentasMfa;
    @Autowired RolesMfaPort rolesMfa;
    @Autowired RevisionCredencialMfaPort revisionMfa;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired ApplicationContext contexto;
    UUID usuario;
    RevisionCredencialMfa revision;
    TransactionTemplate tx() { return new TransactionTemplate(transacciones); }
    @BeforeEach void fixture() {
        usuario=UUID.randomUUID();
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado,correo_verificado) values (?,?,?,?,'ACTIVO',true)",
                usuario,usuario+"@ejemplo.test","Cliente","Prueba");
        jdbc.update("insert into credencial(usuario_id,hash_contrasena,version) values (?,?,7)",usuario,hasher.generar("PruebaMfa9!Exacta").valor());
        jdbc.update("insert into rol(codigo,nombre) values ('GESTOR_COMERCIAL','Gestor comercial') on conflict do nothing");
        jdbc.update("insert into usuario_rol(usuario_id,rol_codigo) values (?,'GESTOR_COMERCIAL')",usuario);
        var previa=credenciales.preverificar(Optional.of(usuario),new SecretoEfimero("PruebaMfa9!Exacta"));
        assertThat(previa.coincide()).isTrue();
        var r=previa.revision().orElseThrow();
        revision=new RevisionCredencialMfa(r.usuarioId(),r.credencialId(),r.version());
    }
    ResultadoCreacionDesafio crear() {
        return tx().execute(s->{ cuentas.bloquear(usuario); return mfa.crearDesafio(usuario,revision); });
    }
    @Test void persisteHashRealSinOtpNiSesionNiResetDeCuenta() {
        assertThat(contexto.getBeansOfType(MfaPort.class)).hasSize(1);
        assertThat(contexto.getBeansOfType(MfaService.class)).isEmpty();
        var creado=(ResultadoCreacionDesafio.Creado)crear();
        var row=almacenamiento.buscarDesafioPorHash(crypto.hashDesafio(creado.desafio().challengeToken())).orElseThrow();
        assertThat(row.correspondeA(usuario,usuario,7)).isTrue();
        assertThat(row.expiraEn()).isEqualTo(AHORA.plusSeconds(300));
        assertThat(creado.desafio().canal()).isEqualTo(CanalMfa.EMAIL);
        assertThat(jdbc.queryForObject("select count(*) from otp",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from token_refresco",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select intentos_fallidos_consecutivos from usuario where id=?",Integer.class,usuario)).isZero();
    }
    @Test void dosLoginsCreanDesafiosDistintosSinConsumirElAnterior() {
        var a=(ResultadoCreacionDesafio.Creado)crear(); var b=(ResultadoCreacionDesafio.Creado)crear();
        assertThat(a.desafio().challengeToken()).isNotEqualTo(b.desafio().challengeToken());
        assertThat(jdbc.queryForObject("select count(*) from desafio_mfa where usuario_id=? and not consumido",Integer.class,usuario)).isEqualTo(2);
    }
    @Test void crearExigeTransaccionDelLoginYNoAbreUnaPropia() {
        assertThatThrownBy(()->mfa.crearDesafio(usuario,revision)).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from desafio_mfa where usuario_id=?",Integer.class,usuario)).isZero();
    }
    @Test void rollbackDelCoordinadorNoDejaDesafioPersistido() {
        tx().executeWithoutResult(s->{ cuentas.bloquear(usuario);
            assertThat(mfa.crearDesafio(usuario,revision)).isInstanceOf(ResultadoCreacionDesafio.Creado.class);s.setRollbackOnly(); });
        assertThat(jdbc.queryForObject("select count(*) from desafio_mfa where usuario_id=?",Integer.class,usuario)).isZero();
    }
    @Test void lecturaDeEstadoYBanderaVoluntariaUsanCuentaYRolesReales() {
        assertThat(mfa.leerEstado(usuario,rolesMfa.leer(usuario))).contains(new EstadoMfa(true,true));
        jdbc.update("delete from usuario_rol where usuario_id=?",usuario);
        assertThat(crear()).isInstanceOf(ResultadoCreacionDesafio.NoRequerido.class);
        jdbc.update("update usuario set mfa_habilitado=true where id=?",usuario);
        assertThat(crear()).isInstanceOf(ResultadoCreacionDesafio.Creado.class);
        assertThat(mfa.esEfectivo(false,Set.of(CLIENTE,ADMIN_SISTEMA))).isTrue();
    }
    @Test void rechazaRevisionCambiadaCorreoNoVerificadoYCuentaNoDisponibleSinCrear() {
        jdbc.update("update credencial set version=8 where usuario_id=?",usuario);
        assertThat(crear()).isEqualTo(ResultadoCreacionDesafio.Rechazado.INVALIDO);
        jdbc.update("update credencial set version=7 where usuario_id=?",usuario);
        jdbc.update("update usuario set correo_verificado=false where id=?",usuario);
        assertThat(crear()).isEqualTo(ResultadoCreacionDesafio.Rechazado.INVALIDO);
        jdbc.update("update usuario set correo_verificado=true,estado='INACTIVO' where id=?",usuario);
        assertThat(crear()).isEqualTo(ResultadoCreacionDesafio.Rechazado.INVALIDO);
        assertThat(jdbc.queryForObject("select count(*) from desafio_mfa where usuario_id=?",Integer.class,usuario)).isZero();
    }
    @Test void desafioPersistidoSeRevalidaContraCredencialActualYVencimiento() {
        var token=((ResultadoCreacionDesafio.Creado)crear()).desafio().challengeToken();
        var validador=new ValidadorDesafioLogin(almacenamiento,crypto,cuentasMfa,rolesMfa,revisionMfa,Clock.fixed(AHORA,ZoneOffset.UTC));
        tx().executeWithoutResult(s->{ cuentas.bloquear(usuario);
            assertThat(validador.localizarVigente(token)).isPresent();
            assertThat(validador.localizarVigente(token+"alterado")).isEmpty(); });
        var vencido=new ValidadorDesafioLogin(almacenamiento,crypto,cuentasMfa,rolesMfa,revisionMfa,Clock.fixed(AHORA.plusSeconds(300),ZoneOffset.UTC));
        tx().executeWithoutResult(s->assertThat(vencido.localizarVigente(token)).isEmpty());
        jdbc.update("update credencial set version=8 where usuario_id=?",usuario);
        tx().executeWithoutResult(s->{ cuentas.bloquear(usuario);assertThat(validador.localizarVigente(token)).isEmpty(); });
    }
}
