package com.marketplace.auth_service.auditoria.adapter.out.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.auditoria.application.port.out.PublicadorEventosPort;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ (catálogo de eventos): exchange topic duradero {@code seguridad.usuarios}, routing key =
 * tipo, mensaje persistente JSON con {@code messageId = eventoId} para que el consumidor deduplique.
 * Usa confirmaciones del broker: solo vuelve cuando el exchange aceptó el mensaje.
 */
public final class PublicadorEventosRabbit implements PublicadorEventosPort {

    public static final String EXCHANGE = "seguridad.usuarios";
    private static final long ESPERA_CONFIRMACION_MS = 5_000;

    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public PublicadorEventosRabbit(RabbitTemplate rabbit, ObjectMapper json) {
        this.rabbit = Objects.requireNonNull(rabbit);
        this.json = Objects.requireNonNull(json);
    }

    @Override
    public void publicar(String tipo, Map<String, Object> sobre) {
        byte[] cuerpo;
        try {
            cuerpo = json.writeValueAsBytes(sobre);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Evento no serializable", e);
        }
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propiedades.setContentEncoding(StandardCharsets.UTF_8.name());
        propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        propiedades.setMessageId(String.valueOf(sobre.get("eventoId")));
        propiedades.setType(tipo);
        Message mensaje = new Message(cuerpo, propiedades);
        rabbit.invoke(operaciones -> {
            operaciones.send(EXCHANGE, tipo, mensaje);
            operaciones.waitForConfirmsOrDie(ESPERA_CONFIRMACION_MS);
            return null;
        });
    }
}
