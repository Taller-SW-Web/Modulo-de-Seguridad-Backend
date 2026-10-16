package com.marketplace.auth_service.auditoria.adapter.out.cola;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.out.ReintentoAuditoriaPort;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Cola privada durable de reintentos (EP-06-T003): {@code seguridad.auditoria.pendiente}.
 * No es evento público ni suscripción G5: solo el worker propio la consume.
 * Publica con confirmación del broker y cabecera {@code intentos = 0}.
 */
public final class ColaReintentoAuditoriaRabbit implements ReintentoAuditoriaPort {

    public static final String COLA = "seguridad.auditoria.pendiente";
    public static final String DLQ = "seguridad.auditoria.dlq";
    public static final String INTENTOS = "intentos";
    private static final long ESPERA_CONFIRMACION_MS = 5_000;

    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public ColaReintentoAuditoriaRabbit(RabbitTemplate rabbit, ObjectMapper json) {
        this.rabbit = Objects.requireNonNull(rabbit);
        this.json = Objects.requireNonNull(json);
    }

    @Override
    public void diferir(RegistroSeguro registro) {
        Objects.requireNonNull(registro, "registro");
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("registroId", registro.registroId().toString());
        cuerpo.put("accion", registro.accion());
        cuerpo.put("resultado", registro.resultado());
        cuerpo.put("actorTipo", registro.actorTipo());
        cuerpo.put("actorId", registro.actorId());
        cuerpo.put("objetivoUsuarioId",
                registro.objetivoUsuarioId() == null ? null : registro.objetivoUsuarioId().toString());
        cuerpo.put("fecha", registro.fecha().toString());
        cuerpo.put("ip", registro.ip());
        cuerpo.put("agente", registro.agente());
        cuerpo.put("detalle", registro.detalle());
        byte[] bytes;
        try {
            bytes = json.writeValueAsBytes(cuerpo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Registro no serializable para reintento", e);
        }
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propiedades.setContentEncoding(StandardCharsets.UTF_8.name());
        propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        propiedades.setMessageId(registro.registroId().toString());
        propiedades.setHeader(INTENTOS, 0);
        rabbit.invoke(operaciones -> {
            operaciones.send(COLA, new Message(bytes, propiedades));
            operaciones.waitForConfirmsOrDie(ESPERA_CONFIRMACION_MS);
            return null;
        });
    }
}
