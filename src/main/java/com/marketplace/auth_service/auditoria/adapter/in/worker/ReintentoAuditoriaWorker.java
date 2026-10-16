package com.marketplace.auth_service.auditoria.adapter.in.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.auditoria.adapter.out.cola.ColaReintentoAuditoriaRabbit;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.out.AuditoriaPersistenciaPort;
import com.marketplace.auth_service.auditoria.domain.model.AccionAuditable;
import com.marketplace.auth_service.auditoria.domain.model.ActorTipo;
import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import com.marketplace.auth_service.auditoria.domain.model.Resultado;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reintento durable de auditoría no crítica (EP-06-T003): sondea la cola
 * privada, inserta con deduplicación y reintenta 10/30/60 s; al cuarto fallo
 * rechaza a la DLQ. Nunca revierte el hecho ya confirmado.
 *
 * <p>Orden garantizado: el ack (o la republicación confirmada) ocurre siempre
 * <b>después</b> de confirmar la transacción de PostgreSQL, y la republicación
 * espera confirmación del broker <b>antes</b> de confirmar el original. Así un
 * fallo de commit o de republicación nunca hace desaparecer el registro.
 */
public class ReintentoAuditoriaWorker {

    private static final Logger log = LoggerFactory.getLogger(ReintentoAuditoriaWorker.class);
    static final Duration[] ESPERAS = {Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(60)};
    static final int MAX_INTENTOS = 3;
    private static final long ESPERA_CONFIRMACION_MS = 5_000;

    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final AuditoriaPersistenciaPort persistencia;
    private final TransactionTemplate txNueva;

    public ReintentoAuditoriaWorker(RabbitTemplate rabbit, ObjectMapper json,
                                    AuditoriaPersistenciaPort persistencia,
                                    PlatformTransactionManager transacciones) {
        this.rabbit = Objects.requireNonNull(rabbit);
        this.json = Objects.requireNonNull(json);
        this.persistencia = Objects.requireNonNull(persistencia);
        Objects.requireNonNull(transacciones, "transacciones");
        this.txNueva = new TransactionTemplate(transacciones);
    }

    @Scheduled(fixedDelayString = "${g7.auditoria.reintento-ms:2000}")
    public void reintentar() {
        try {
            rabbit.execute(channel -> {
                GetResponse respuesta = channel.basicGet(ColaReintentoAuditoriaRabbit.COLA, false);
                if (respuesta == null) {
                    return 0;
                }
                procesar(channel, respuesta);
                return 1;
            });
        } catch (RuntimeException e) {
            log.warn("auditoría: ciclo de reintento fallido ({})", e.getClass().getSimpleName());
        }
    }

    private void procesar(Channel canal, GetResponse respuesta) throws Exception {
        long entrega = respuesta.getEnvelope().getDeliveryTag();
        int intentos = intentosDe(respuesta.getProps());
        RegistroSeguro dto;
        try {
            dto = leer(respuesta.getBody());
        } catch (RuntimeException e) {
            canal.basicReject(entrega, false);
            log.warn("auditoría: mensaje ilegible a DLQ ({})", e.getClass().getSimpleName());
            return;
        }
        boolean yaExiste;
        try {
            yaExiste = existe(dto.registroId());
        } catch (RuntimeException e) {
            canal.basicNack(entrega, false, true);
            log.warn("auditoría: deduplicación fallida, mensaje reencolado ({})", e.getClass().getSimpleName());
            return;
        }
        if (yaExiste) {
            canal.basicAck(entrega, false);
            return;
        }
        try {
            guardar(dto);
            canal.basicAck(entrega, false);
        } catch (RuntimeException e) {
            if (intentos >= MAX_INTENTOS) {
                canal.basicReject(entrega, false);
                log.warn("auditoría: registro {} a DLQ tras {} intentos", dto.registroId(), intentos);
                return;
            }
            try {
                Thread.sleep(ESPERAS[intentos].toMillis());
            } catch (InterruptedException interrumpido) {
                Thread.currentThread().interrupt();
                canal.basicNack(entrega, false, true);
                return;
            }
            try {
                reencolarConfirmado(canal, dto, intentos + 1);
                canal.basicAck(entrega, false);
            } catch (Exception republicado) {
                canal.basicNack(entrega, false, true);
                log.warn("auditoría: republicación fallida, mensaje reencolado ({})",
                        republicado.getClass().getSimpleName());
            }
        }
    }

    private boolean existe(UUID registroId) {
        return Boolean.TRUE.equals(txNueva.execute(estado -> persistencia.existeRegistro(registroId)));
    }

    private void guardar(RegistroSeguro dto) {
        txNueva.executeWithoutResult(estado -> persistencia.guardar(aModelo(dto)));
    }

    private int intentosDe(com.rabbitmq.client.BasicProperties propiedades) {
        Object valor = propiedades.getHeaders().get(ColaReintentoAuditoriaRabbit.INTENTOS);
        return valor instanceof Number n ? n.intValue() : 0;
    }

    private RegistroSeguro leer(byte[] cuerpo) {
        Map<String, Object> mapa;
        try {
            mapa = json.readValue(new String(cuerpo, StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new IllegalArgumentException("cuerpo ilegible", e);
        }
        try {
            return new RegistroSeguro(UUID.fromString(texto(mapa, "registroId")),
                    texto(mapa, "accion"), texto(mapa, "resultado"), texto(mapa, "actorTipo"),
                    (String) mapa.get("actorId"),
                    mapa.get("objetivoUsuarioId") == null ? null : UUID.fromString(texto(mapa, "objetivoUsuarioId")),
                    Instant.parse(texto(mapa, "fecha")), (String) mapa.get("ip"),
                    (String) mapa.get("agente"),
                    detalleDe(mapa.get("detalle")));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("registro inválido", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> detalleDe(Object detalle) {
        if (detalle == null) {
            return Map.of();
        }
        if (detalle instanceof Map<?, ?> mapa) {
            Map<String, String> copia = new LinkedHashMap<>();
            mapa.forEach((clave, valor) -> copia.put(String.valueOf(clave), String.valueOf(valor)));
            return copia;
        }
        throw new IllegalArgumentException("detalle inválido");
    }

    private static String texto(Map<String, Object> mapa, String clave) {
        Object valor = mapa.get(clave);
        if (valor == null) {
            throw new IllegalArgumentException("falta " + clave);
        }
        return valor.toString();
    }

    private static RegistroAuditoria aModelo(RegistroSeguro dto) {
        return RegistroAuditoria.nuevo(dto.registroId(), dto.accion(),
                Resultado.valueOf(dto.resultado()), ActorTipo.valueOf(dto.actorTipo()),
                dto.actorId(), dto.objetivoUsuarioId(), dto.fecha(), dto.ip(), dto.agente(),
                dto.detalle());
    }

    private void reencolarConfirmado(Channel canal, RegistroSeguro dto, int intentos) throws Exception {
        Map<String, Object> cabeceras = new LinkedHashMap<>();
        cabeceras.put(ColaReintentoAuditoriaRabbit.INTENTOS, intentos);
        com.rabbitmq.client.AMQP.BasicProperties propiedades =
                new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                        .contentType(MessageProperties.CONTENT_TYPE_JSON)
                        .deliveryMode(2) // PERSISTENT
                        .messageId(dto.registroId().toString())
                        .headers(cabeceras)
                        .build();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("registroId", dto.registroId().toString());
        cuerpo.put("accion", dto.accion());
        cuerpo.put("resultado", dto.resultado());
        cuerpo.put("actorTipo", dto.actorTipo());
        cuerpo.put("actorId", dto.actorId());
        cuerpo.put("objetivoUsuarioId", dto.objetivoUsuarioId() == null ? null : dto.objetivoUsuarioId().toString());
        cuerpo.put("fecha", dto.fecha().toString());
        cuerpo.put("ip", dto.ip());
        cuerpo.put("agente", dto.agente());
        cuerpo.put("detalle", dto.detalle());
        canal.confirmSelect();
        canal.basicPublish("", ColaReintentoAuditoriaRabbit.COLA, propiedades,
                json.writeValueAsBytes(cuerpo));
        canal.waitForConfirmsOrDie(ESPERA_CONFIRMACION_MS);
    }
}
