package com.marketplace.auth_service.auditoria.adapter.out.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TEC-INFRA-T004 contra un RabbitMQ real (3.13.7, la versión del diseño): el evento llega al
 * exchange topic {@code seguridad.usuarios} con su routing key, persistente, con messageId =
 * eventoId y el sobre del catálogo; sin exchange no hay confirmación y no se da por publicado.
 */
@Testcontainers(disabledWithoutDocker = true)
class PublicadorEventosRabbitTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13.7-management");

    private static CachingConnectionFactory conexiones;
    private static RabbitTemplate rabbit;
    private static RabbitAdmin admin;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void conectar() {
        conexiones = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        conexiones.setUsername(RABBIT.getAdminUsername());
        conexiones.setPassword(RABBIT.getAdminPassword());
        conexiones.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);
        rabbit = new RabbitTemplate(conexiones);
        admin = new RabbitAdmin(conexiones);
    }

    @AfterAll
    static void cerrar() {
        conexiones.destroy();
    }

    private static Map<String, Object> sobre(UUID eventoId) {
        Map<String, Object> sobre = new LinkedHashMap<>();
        sobre.put("eventoId", eventoId.toString());
        sobre.put("tipo", "usuario.roles_cambiados");
        sobre.put("version", 1);
        sobre.put("fecha", "2026-10-17T15:00:00Z");
        sobre.put("usuarioId", "00000000-0000-4000-8000-00000000f00b");
        sobre.put("datos", Map.of("roles", List.of("CLIENTE", "VENDEDOR")));
        return sobre;
    }

    @Test
    void publicaEnElExchangeDelCatalogoYLoRecibeUnaColaConBindingUsuarioAsterisco() throws Exception {
        TopicExchange exchange = new TopicExchange(PublicadorEventosRabbit.EXCHANGE, true, false);
        Queue cola = new Queue("despacho.usuarios", true);
        admin.declareExchange(exchange);
        admin.declareQueue(cola);
        admin.declareBinding(BindingBuilder.bind(cola).to(exchange).with("usuario.*"));
        UUID eventoId = UUID.randomUUID();

        new PublicadorEventosRabbit(rabbit, json).publicar("usuario.roles_cambiados", sobre(eventoId));

        Message recibido = rabbit.receive("despacho.usuarios", 5_000);
        assertThat(recibido).isNotNull();
        assertThat(recibido.getMessageProperties().getReceivedRoutingKey()).isEqualTo("usuario.roles_cambiados");
        assertThat(recibido.getMessageProperties().getMessageId()).isEqualTo(eventoId.toString());
        assertThat(recibido.getMessageProperties().getReceivedDeliveryMode().name()).isEqualTo("PERSISTENT");
        JsonNode cuerpo = json.readTree(recibido.getBody());
        assertThat(cuerpo.get("tipo").asText()).isEqualTo("usuario.roles_cambiados");
        assertThat(cuerpo.get("version").asInt()).isEqualTo(1);
        assertThat(cuerpo.get("datos").get("roles").get(1).asText()).isEqualTo("VENDEDOR");
    }

    @Test
    void sinExchangeElBrokerNoConfirmaYNoSeDaPorPublicado() {
        admin.deleteExchange(PublicadorEventosRabbit.EXCHANGE);
        assertThatThrownBy(() -> new PublicadorEventosRabbit(rabbit, json)
                .publicar("usuario.desbloqueado", sobre(UUID.randomUUID())))
                .isInstanceOf(RuntimeException.class);
        admin.declareExchange(new TopicExchange(PublicadorEventosRabbit.EXCHANGE, true, false));
    }
}
