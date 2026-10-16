package com.marketplace.auth_service.auditoria.adapter.in.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.auditoria.adapter.out.cola.ColaReintentoAuditoriaRabbit;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.AuditoriaJpaAdapter;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.BaseAuditoriaPostgres;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EP-06-T003 contra PostgreSQL y RabbitMQ reales: diferir publica con
 * confirmación, el worker inserta con deduplicación y el veneno cae a DLQ.
 * Requiere Docker; en local sin Docker corre en CI.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReintentoAuditoriaWorkerTest.InfraPropia.class, AuditoriaJpaAdapter.class,
        ColaReintentoAuditoriaRabbit.class, ReintentoAuditoriaWorker.class})
@Testcontainers
class ReintentoAuditoriaWorkerTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.auditoria.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.auditoria.adapter.out.persistence.repository")
    static class InfraPropia {
        @Bean
        ConnectionFactory conexionRabbit() {
            CachingConnectionFactory fabrica = new CachingConnectionFactory(
                    BROKER.getHost(), BROKER.getAmqpPort());
            fabrica.setUsername(BROKER.getAdminUsername());
            fabrica.setPassword(BROKER.getAdminPassword());
            return fabrica;
        }

        @Bean
        RabbitAdmin adminRabbit(ConnectionFactory conexion) {
            return new RabbitAdmin(conexion);
        }

        @Bean
        RabbitTemplate plantillaRabbit(ConnectionFactory conexion) {
            return new RabbitTemplate(conexion);
        }

        @Bean
        Queue pendiente() {
            return QueueBuilder.durable(ColaReintentoAuditoriaRabbit.COLA)
                    .deadLetterExchange("").deadLetterRoutingKey(ColaReintentoAuditoriaRabbit.DLQ).build();
        }

        @Bean
        Queue dlq() {
            return QueueBuilder.durable(ColaReintentoAuditoriaRabbit.DLQ).build();
        }

        @Bean
        ObjectMapper json() {
            return new ObjectMapper();
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    @Container
    static final RabbitMQContainer BROKER =
            new RabbitMQContainer("rabbitmq:3.13.7-management");
    private static final Instant AHORA = Instant.parse("2026-10-17T12:00:00Z");

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        BROKER.start();
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            BaseAuditoriaPostgres.cargarEsquema(conexion);
            BaseAuditoriaPostgres.migrar(conexion);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar el PostgreSQL de prueba de auditoría", e);
        }
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ColaReintentoAuditoriaRabbit cola;
    @Autowired ReintentoAuditoriaWorker worker;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcTemplate jdbc;

    private static RegistroSeguro dto() {
        return new RegistroSeguro(UUID.randomUUID(), "SESION_FALLIDA", "FALLO", "USUARIO",
                "sub-1", UUID.randomUUID(), AHORA, "10.0.0.1", "agent", Map.of());
    }

    private long enCola(String nombre) {
        return rabbit.execute(canal ->
                canal.queueDeclarePassive(nombre).getMessageCount());
    }

    @Test
    void diferirPublicaConConfirmacionYElWorkerInsertaDeduplicando() {
        var dto = dto();
        cola.diferir(dto);
        assertThat(enCola(ColaReintentoAuditoriaRabbit.COLA)).isEqualTo(1L);

        worker.reintentar();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_seguridad", Long.class))
                .isEqualTo(1L);
        assertThat(enCola(ColaReintentoAuditoriaRabbit.COLA)).isZero();
        worker.reintentar();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_seguridad", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void mensajeIlegibleCaeADlqSinReintentos() {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        rabbit.send(ColaReintentoAuditoriaRabbit.COLA,
                new Message("no-json".getBytes(StandardCharsets.UTF_8), propiedades));

        worker.reintentar();

        assertThat(enCola(ColaReintentoAuditoriaRabbit.COLA)).isZero();
        assertThat(enCola(ColaReintentoAuditoriaRabbit.DLQ)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_seguridad", Long.class))
                .isZero();
    }
}
