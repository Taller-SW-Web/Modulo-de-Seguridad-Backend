package com.marketplace.auth_service.auditoria.adapter.out.persistence;

import com.marketplace.auth_service.auditoria.domain.model.AccionAuditable;
import com.marketplace.auth_service.auditoria.domain.model.ActorTipo;
import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import com.marketplace.auth_service.auditoria.domain.model.Resultado;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Locale;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EP-06-T001 sobre PostgreSQL real: V106 con histórico previo, constraints,
 * trigger solo-anexado y validación JPA (ddl-auto=validate).
 * Usa {@code PRUEBAS_POSTGRES_URL} (nunca Supabase) o Docker; sin ninguno se omite.
 * Cada clase crea su propia base desechable.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuditoriaJpaAdapter.class, AuditoriaPersistenciaPostgresTest.PersistenciaPropia.class})
@RequierePostgres
class AuditoriaPersistenciaPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.auditoria.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.auditoria.adapter.out.persistence.repository")
    static class PersistenciaPropia { }

    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");
    private static PostgreSQLContainer<?> contenedor;

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) throws Exception {
        String url = System.getenv("PRUEBAS_POSTGRES_URL");
        String usuario;
        String contrasena;
        if (url != null) {
            usuario = System.getenv().getOrDefault("PRUEBAS_POSTGRES_USUARIO", "postgres");
            contrasena = System.getenv().getOrDefault("PRUEBAS_POSTGRES_CONTRASENA", "postgres");
        } else if (DockerClientFactory.instance().isDockerAvailable()) {
            contenedor = new PostgreSQLContainer<>("postgres:17.6");
            contenedor.start();
            url = contenedor.getJdbcUrl();
            usuario = contenedor.getUsername();
            contrasena = contenedor.getPassword();
        } else {
            return;
        }
        String nombre = "auditoria_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        try (var admin = DriverManager.getConnection(url, usuario, contrasena); var s = admin.createStatement()) {
            s.execute("CREATE DATABASE " + nombre);
        }
        String propia = url.replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
        try (var conexion = DriverManager.getConnection(propia, usuario, contrasena)) {
            BaseAuditoriaPostgres.cargarEsquema(conexion);
            try (var sentencia = conexion.createStatement()) {
                sentencia.execute("INSERT INTO auditoria_seguridad (accion, resultado, actor_tipo)"
                        + " VALUES ('SESION_FALLIDA', 'FALLO', 'USUARIO')");
            }
            BaseAuditoriaPostgres.migrar(conexion);
        }
        registro.add("spring.datasource.url", () -> propia);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
    }

    @Autowired
    AuditoriaJpaAdapter adaptador;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transacciones;

    private static RegistroAuditoria registro() {
        return new RegistroAuditoria(UUID.randomUUID(), AccionAuditable.ROL_ASIGNADO,
                Resultado.EXITO, ActorTipo.USUARIO, "sub-1", UUID.randomUUID(), AHORA,
                "10.0.0.1", "agent", Map.of("rol", "VENDEDOR"));
    }

    @Test
    void historicoRecibeRegistroIdYMigraSinPerderFilas() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_seguridad WHERE accion = 'SESION_FALLIDA'",
                Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM auditoria_seguridad WHERE registro_id IS NULL", Long.class))
                .isEqualTo(0L);
        long id = adaptador.guardar(registro());
        assertThat(id).isPositive();
        var propio = registro();
        adaptador.guardar(propio);
        assertThat(adaptador.existeRegistro(propio.registroId())).isTrue();
    }

    /** Cada guardado en su propia transacción: tras el fallo, la de la prueba no queda abortada y se puede contar. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registroIdDuplicadoNoCreaSegundaFila() {
        var primero = registro();
        var transaccion = new TransactionTemplate(transacciones);
        transaccion.executeWithoutResult(estado -> adaptador.guardar(primero));
        assertThat(adaptador.existeRegistro(primero.registroId())).isTrue();
        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> adaptador.guardar(primero)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_seguridad WHERE registro_id = ?",
                Long.class, primero.registroId())).isEqualTo(1L);
    }

    @Test
    void updateDirectoLoRechazaElTrigger() {
        assertThatThrownBy(() -> jdbc.execute("UPDATE auditoria_seguridad SET accion = 'SESION_FALLIDA'"))
                .hasMessageContaining("solo anexado");
    }
}
