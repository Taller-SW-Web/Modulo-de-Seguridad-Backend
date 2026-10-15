package com.marketplace.auth_service.auditoria.adapter.out.persistence;

import com.marketplace.auth_service.auditoria.domain.model.AccionAuditable;
import com.marketplace.auth_service.auditoria.domain.model.ActorTipo;
import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import com.marketplace.auth_service.auditoria.domain.model.Resultado;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EP-06-T001 sobre PostgreSQL real: V106 con histórico previo, constraints,
 * trigger solo-anexado y validación JPA (ddl-auto=validate).
 * Requiere Docker; en local sin Docker corre en CI.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuditoriaJpaAdapter.class, AuditoriaPersistenciaPostgresTest.PersistenciaPropia.class})
@Testcontainers
class AuditoriaPersistenciaPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.auditoria.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.auditoria.adapter.out.persistence.repository")
    static class PersistenciaPropia { }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            BaseAuditoriaPostgres.cargarEsquema(conexion);
            try (var sentencia = conexion.createStatement()) {
                sentencia.execute("INSERT INTO auditoria_seguridad (accion, resultado, actor_tipo)"
                        + " VALUES ('SESION_FALLIDA', 'FALLO', 'USUARIO')");
            }
            BaseAuditoriaPostgres.migrar(conexion);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar el PostgreSQL de prueba de auditoría", e);
        }
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static RegistroAuditoria registro() {
        return new RegistroAuditoria(UUID.randomUUID(), AccionAuditable.ROL_ASIGNADO,
                Resultado.EXITO, ActorTipo.USUARIO, "sub-1", UUID.randomUUID(), AHORA,
                "10.0.0.1", "agent", Map.of("rol", "VENDEDOR"));
    }

    @Test
    void historicoRecibeRegistroIdYMigraSinPerderFilas(AuditoriaJpaAdapter adaptador, JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_seguridad", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM auditoria_seguridad WHERE registro_id IS NULL", Long.class))
                .isEqualTo(0L);
        long id = adaptador.guardar(registro());
        assertThat(id).isPositive();
        var propio = registro();
        adaptador.guardar(propio);
        assertThat(adaptador.existeRegistro(propio.registroId())).isTrue();
    }

    @Test
    void registroIdDuplicadoNoCreaSegundaFila(AuditoriaJpaAdapter adaptador) {
        var primero = registro();
        adaptador.guardar(primero);
        assertThat(adaptador.existeRegistro(primero.registroId())).isTrue();
        assertThatThrownBy(() -> adaptador.guardar(primero))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateDirectoLoRechazaElTrigger(JdbcTemplate jdbc) {
        assertThatThrownBy(() -> jdbc.execute("UPDATE auditoria_seguridad SET accion = 'SESION_FALLIDA'"))
                .hasMessageContaining("solo anexado");
    }
}
