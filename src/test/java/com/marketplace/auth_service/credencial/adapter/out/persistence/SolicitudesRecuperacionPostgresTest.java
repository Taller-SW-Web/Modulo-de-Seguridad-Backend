package com.marketplace.auth_service.credencial.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.credencial.adapter.out.crypto.HmacCorreoSolicitudJca;
import com.marketplace.auth_service.credencial.application.port.out.CuotaRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.HashCorreoSolicitudPort;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@JdbcTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SolicitudesRecuperacionPostgresTest.ConfiguracionCuota.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SolicitudesRecuperacionPostgresTest {
    private static final Instant AHORA = Instant.parse("2026-10-21T12:00:00Z");
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6");

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionCuota {
        @Bean HashCorreoSolicitudPort hashCorreoSolicitudPort() {
            return new HmacCorreoSolicitudJca("clave-test-de-32-bytes-como-minimo".getBytes(StandardCharsets.UTF_8));
        }
        @Bean CuotaRecuperacionPort cuotaRecuperacionPort(JdbcTemplate jdbc, HashCorreoSolicitudPort hash) {
            return new SolicitudesRecuperacionPostgresAdapter(jdbc, hash);
        }
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry r) {
        POSTGRES.start();
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var consulta = conexion.createStatement();
             var esquema = new ClassPathResource("mfa/base-observada/schema.sql").getInputStream()) {
            consulta.execute(new String(esquema.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar PostgreSQL aislado para solicitud_limitada", e);
        }
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CuotaRecuperacionPort cuota;
    @Autowired PlatformTransactionManager transacciones;
    private TransactionTemplate tx;

    @BeforeEach
    void limpiar() {
        jdbc.execute("truncate table solicitud_limitada restart identity");
        tx = new TransactionTemplate(transacciones);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Test
    void permiteTresYRechazaLaCuartaConRetryAfterSinGuardarElCorreo() {
        for (int i = 0; i < 3; i++) assertThat(reservar("persona@example.test", AHORA)).isEqualTo(Duration.ZERO);

        Duration espera = reservar("persona@example.test", AHORA);

        assertThat(espera).isEqualTo(Duration.ofHours(1));
        assertThat(jdbc.queryForObject("select count(*) from solicitud_limitada", Long.class)).isEqualTo(3L);
        String guardado = jdbc.queryForObject("select string_agg(clave_hash, ',') from solicitud_limitada", String.class);
        assertThat(guardado).doesNotContain("persona", "example").matches("[0-9a-f,]+");
    }

    @Test
    void laCuotaEsPorTipoYCorreoYSeLiberaAlCumplirUnaHora() {
        for (int i = 0; i < 3; i++) assertThat(reservar("persona@example.test", AHORA)).isEqualTo(Duration.ZERO);

        assertThat(reservar("otra@example.test", AHORA)).isEqualTo(Duration.ZERO);
        assertThat(reservar("persona@example.test", AHORA.plus(Duration.ofHours(1)))).isEqualTo(Duration.ZERO);
        assertThat(jdbc.queryForObject("select count(*) from solicitud_limitada where tipo='RECUPERACION'::tipo_limite",
                Long.class)).isEqualTo(5L);
    }

    @Test
    void serializaSolicitudesConcurrentesParaQueSoloTresReservasPasen() throws Exception {
        int cantidad = 8;
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(cantidad);
        try {
            var futuros = new ArrayList<java.util.concurrent.Future<Duration>>();
            for (int i = 0; i < cantidad; i++) {
                futuros.add(pool.submit(() -> {
                    inicio.await();
                    return reservar("concurrente@example.test", AHORA);
                }));
            }
            inicio.countDown();
            var resultados = new ArrayList<Duration>();
            for (var futuro : futuros) resultados.add(futuro.get(30, TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(Duration.ZERO::equals)).hasSize(3);
            assertThat(resultados.stream().filter(d -> !d.isZero())).hasSize(5);
            assertThat(jdbc.queryForObject("select count(*) from solicitud_limitada", Long.class)).isEqualTo(3L);
        } finally {
            pool.shutdownNow();
        }
    }

    private Duration reservar(String correo, Instant ahora) {
        return tx.execute(status -> cuota.reservar(correo, ahora));
    }
}
