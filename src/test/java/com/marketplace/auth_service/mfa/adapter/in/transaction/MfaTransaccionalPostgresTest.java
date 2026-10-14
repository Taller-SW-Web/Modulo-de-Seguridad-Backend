package com.marketplace.auth_service.mfa.adapter.in.transaction;

import com.marketplace.auth_service.mfa.adapter.out.crypto.CriptografiaMfaJca;
import com.marketplace.auth_service.mfa.adapter.out.persistence.MfaJpaAdapter;
import com.marketplace.auth_service.mfa.application.dto.*;
import com.marketplace.auth_service.mfa.application.port.in.MfaPort;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.mfa.application.service.MfaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
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
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({MfaJpaAdapter.class,MfaTransaccionalPostgresTest.SoloPruebas.class})
@Testcontainers
class MfaTransaccionalPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16.6");
    private static final Instant AHORA=Instant.parse("2026-10-14T05:00:00Z");
    @Autowired MfaPort mfa;
    @Autowired MfaJpaAdapter almacenamiento;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    UUID usuario,credencial;
    RevisionCredencialMfa revision;

    @DynamicPropertySource static void base(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        try(var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())) {
            for(String archivo:new String[]{"V1__usuario_y_roles.sql","V2__credenciales_otp_tokens.sql",
                    "V3__sesion_y_bloqueo.sql","V4__auditoria_y_integracion.sql"}) {
                try(var entrada=new ClassPathResource("mfa-t002/base-historica/"+archivo).getInputStream();
                    var sentencia=c.createStatement()) {
                    sentencia.execute(new String(entrada.readAllBytes(),StandardCharsets.UTF_8));
                }
            }
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migraciones-propuestas/V103__mfa_desafio_otp.sql"));
        } catch(Exception e) { throw new IllegalStateException("No se pudo preparar la base local para T002",e); }
        registro.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username",POSTGRES::getUsername);
        registro.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @BeforeEach void fixture() {
        usuario=UUID.randomUUID(); credencial=UUID.randomUUID();
        revision=new RevisionCredencialMfa(usuario,credencial,7);
        // Datos referenciados de prueba; no implementación ni repositorio del dueño ajeno.
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado) values (?,?,?,?,'ACTIVO')",
                usuario,usuario+"@ejemplo.test","Cliente","Prueba");
        jdbc.update("insert into credencial(id,usuario_id,password_hash,algoritmo) values (?,?,?,?)",
                credencial,usuario,"hash-de-fixture-no-utilizable","FIXTURE");
    }

    @Test void persisteHashRealSinOtpNiSesionNiResetDeCuenta() {
        var creado=(ResultadoCreacionDesafio.Creado)mfa.crearDesafio(usuario,revision);
        var crypto=new CriptografiaMfaJca("test",Map.of("test",new byte[32]),new SecureRandom());
        var row=almacenamiento.buscarDesafioPorHash(crypto.hashDesafio(creado.desafio().challengeToken())).orElseThrow();
        assertThat(row.correspondeA(usuario,credencial,7)).isTrue();
        assertThat(row.expiraEn()).isEqualTo(AHORA.plusSeconds(300));
        assertThat(jdbc.queryForObject("select count(*) from otp",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from token_refresco",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select intentos_fallidos_consecutivos from usuario where id=?",Integer.class,usuario)).isZero();
    }
    @Test void dosLoginsCreanDesafiosDistintosSinConsumirElAnterior() {
        var a=(ResultadoCreacionDesafio.Creado)mfa.crearDesafio(usuario,revision);
        var b=(ResultadoCreacionDesafio.Creado)mfa.crearDesafio(usuario,revision);
        assertThat(a.desafio().challengeToken()).isNotEqualTo(b.desafio().challengeToken());
        assertThat(jdbc.queryForObject("select count(*) from desafio_mfa where usuario_id=? and not consumido",
                Integer.class,usuario)).isEqualTo(2);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void crearExigeTransaccionDelLoginYNoAbreUnaPropia() {
        assertThatThrownBy(() -> mfa.crearDesafio(usuario,revision)).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from desafio_mfa where usuario_id=?",Integer.class,usuario)).isZero();
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void rollbackDelCoordinadorNoDejaDesafioPersistido() {
        var tx=new TransactionTemplate(transacciones);
        tx.executeWithoutResult(s -> {
            assertThat(mfa.crearDesafio(usuario,revision)).isInstanceOf(ResultadoCreacionDesafio.Creado.class);
            s.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from desafio_mfa where usuario_id=?",Integer.class,usuario)).isZero();
    }

    @TestConfiguration(proxyBeanMethods=false)
    static class SoloPruebas {
        @Bean MfaPort mfaPort(MfaJpaAdapter almacenamiento) {
            // Dobles exclusivamente de test; no acreditan los proveedores de Eva/Juan/Sergio.
            CuentasMfaPort cuentas=id -> Optional.of(new CuentaMfaSnapshot(id,true,true,false));
            RolesMfaPort roles=id -> Set.of("GESTOR_COMERCIAL");
            RevisionCredencialMfaPort credenciales=r -> true;
            var crypto=new CriptografiaMfaJca("test",Map.of("test",new byte[32]),new SecureRandom());
            var core=new MfaService(cuentas,roles,credenciales,almacenamiento,crypto,
                    Clock.fixed(AHORA,ZoneOffset.UTC),UUID::randomUUID);
            return new MfaTransaccional(core);
        }
    }
}
