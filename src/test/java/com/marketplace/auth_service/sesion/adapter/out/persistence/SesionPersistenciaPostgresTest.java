package com.marketplace.auth_service.sesion.adapter.out.persistence;

import com.marketplace.auth_service.sesion.domain.model.AccesoSesion;
import com.marketplace.auth_service.sesion.domain.model.SesionFamilia;
import com.marketplace.auth_service.sesion.domain.model.TokenRefresco;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EP-02-T002 sobre PostgreSQL real: V102 con datos previos coherentes,
 * constraints, FK compuesta y validación JPA (ddl-auto=validate).
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SesionJpaAdapter.class, SesionPersistenciaPostgresTest.PersistenciaPropia.class})
@Testcontainers
@RequiereSesionPostgres
class SesionPersistenciaPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.sesion.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.sesion.adapter.out.persistence.repository")
    static class PersistenciaPropia { }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final UUID FAMILIA_A = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID USUARIO_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID FAMILIA_B = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("sesion/esquema-previo-v102.sql"));
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("sesion/historicos-coherentes-v102.sql"));
            ScriptUtils.executeSqlScript(conexion,
                    new FileSystemResource("docs/migraciones-propuestas/V102__sesion_familia_acceso.sql"));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar el PostgreSQL de prueba de sesión", e);
        }
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired SesionJpaAdapter persistencia;
    @Autowired JdbcTemplate jdbc;

    @Test
    void backfillConservaRevocacionHistorica() {
        var raiz = persistencia.bloquearFamilia(FAMILIA_A).orElseThrow();
        assertThat(raiz.revocada()).isTrue();
        assertThat(raiz.revocadaEn()).isNotNull();
        assertThat(persistencia.bloquearFamilia(UUID.randomUUID())).isEmpty();
    }

    @Test
    void backfillDejaActivaLaFamiliaSinRevocaciones() {
        var raiz = persistencia.bloquearFamilia(FAMILIA_B).orElseThrow();
        assertThat(raiz.revocada()).isFalse();
        assertThat(raiz.revocadaEn()).isNull();
    }

    @Test
    void persisteFamiliaTokenYAccesoAsociados() {
        UUID familia = UUID.randomUUID();
        UUID usuario = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?,?,?,?, 'ACTIVO')",
                usuario, usuario + "@ejemplo.test", "Cliente", "Prueba");
        persistencia.guardarFamilia(SesionFamilia.nueva(familia, usuario, AHORA));
        var token = TokenRefresco.nuevo(UUID.randomUUID(), usuario, familia, "d".repeat(64), AHORA);
        persistencia.guardarToken(token);
        var acceso = AccesoSesion.nuevo(UUID.randomUUID(), usuario, familia, AHORA);
        persistencia.guardarAcceso(acceso);
        assertThat(persistencia.buscarTokenPorHash("d".repeat(64))).contains(token);
        assertThat(persistencia.buscarAccesoPorJti(acceso.jti())).contains(acceso);
        assertThat(persistencia.buscarTokenPorHash("e".repeat(64))).isEmpty();
        assertThat(persistencia.buscarAccesoPorJti(UUID.randomUUID())).isEmpty();
    }

    @Test
    void fkCompuestaRechazaTokenDeOtroUsuario() {
        UUID ajeno = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?,?,?,?, 'ACTIVO')",
                ajeno, ajeno + "@ejemplo.test", "Cliente", "Prueba");
        var token = TokenRefresco.nuevo(UUID.randomUUID(), ajeno, FAMILIA_A, "f".repeat(64), AHORA);
        assertThatThrownBy(() -> persistencia.guardarToken(token))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("fk_token_refresco_familia");
    }

    @Test
    void accesoExigeFamiliaCoherente() {
        var incoherente = new AccesoSesion(UUID.randomUUID(), UUID.randomUUID(), FAMILIA_B,
                AHORA.plusSeconds(900), AHORA);
        assertThatThrownBy(() -> persistencia.guardarAcceso(incoherente))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("fk_acceso_sesion_familia");
    }

    @Test
    void accesoExigeExpiracionPosteriorACreado() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into acceso_sesion(jti, usuario_id, familia_id, expira_en, creado_en) values (?,?,?,?, now())",
                UUID.randomUUID(), USUARIO_A, FAMILIA_B, AHORA.minusSeconds(1)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("ck_acceso_sesion_expiracion");
    }

    @Test
    void checkDeRevocadaRechazaFilaIncoherente() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into sesion_familia(id, usuario_id, revocada, creado_en) values (?,?, true, now())",
                UUID.randomUUID(), USUARIO_A))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("ck_sesion_familia_revocada");
    }

    @Test
    void jtiDeAccesoEsUnico() {
        var acceso = AccesoSesion.nuevo(UUID.randomUUID(), USUARIO_A, FAMILIA_A, AHORA);
        persistencia.guardarAcceso(acceso);
        assertThatThrownBy(() -> jdbc.update(
                "insert into acceso_sesion(jti, usuario_id, familia_id, expira_en, creado_en) values (?,?,?,?,?)",
                acceso.jti(), USUARIO_A, FAMILIA_A,
                java.time.OffsetDateTime.ofInstant(AHORA.plusSeconds(900), java.time.ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(AHORA, java.time.ZoneOffset.UTC)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void objetosDelDisenoExisten() {
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where schemaname='public' "
                + "and tablename='acceso_sesion' and indexname in "
                + "('idx_acceso_sesion_familia','idx_acceso_sesion_expira')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname='uq_sesion_familia_id_usuario'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname='fk_token_refresco_familia'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void guardadoYLockExigenTransaccionDelCoordinador() {
        assertThatThrownBy(() -> persistencia.guardarFamilia(SesionFamilia.nueva(UUID.randomUUID(), USUARIO_A, AHORA)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> persistencia.bloquearFamilia(FAMILIA_A))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
