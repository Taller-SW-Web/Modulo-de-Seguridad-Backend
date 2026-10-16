package com.marketplace.auth_service.auditoria.adapter.in.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.auditoria.adapter.out.cola.ColaReintentoAuditoriaRabbit;
import com.marketplace.auth_service.auditoria.application.port.out.AuditoriaPersistenciaPort;
import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recuperación explícita del worker (revisión PR #52): ningún fallo deja el
 * mensaje abandonado sin ack. Puro unitario, sin Docker.
 */
class ReintentoAuditoriaRecuperacionTest {

    private static final String CUERPO = """
            {"registroId":"%s","accion":"SESION_FALLIDA","resultado":"FALLO","actorTipo":"SISTEMA",
             "actorId":null,"objetivoUsuarioId":null,"fecha":"2026-10-17T12:00:00Z",
             "ip":null,"agente":null,"detalle":{}}""";

    static class Puertos {
        final Channel canal = mock(Channel.class);
        final RabbitTemplate rabbit = mock(RabbitTemplate.class);
        final AuditoriaPersistenciaPort persistencia = mock(AuditoriaPersistenciaPort.class);
        final ReintentoAuditoriaWorker worker;

        Puertos() throws Exception {
            when(rabbit.execute(any())).thenAnswer(inv -> {
                org.springframework.amqp.rabbit.core.ChannelCallback<Object> cb = inv.getArgument(0);
                return cb.doInRabbit(canal);
            });
            worker = new ReintentoAuditoriaWorker(rabbit, new ObjectMapper(), persistencia,
                    mock(PlatformTransactionManager.class));
        }

        GetResponse mensaje(String cuerpo, int intentos) {
            Map<String, Object> cabeceras = new LinkedHashMap<>();
            cabeceras.put("intentos", intentos);
            var props = new AMQP.BasicProperties.Builder().headers(cabeceras).build();
            return new GetResponse(new Envelope(7L + intentos, false, "", ColaReintentoAuditoriaRabbit.COLA),
                    props, cuerpo.getBytes(StandardCharsets.UTF_8), 1);
        }
    }

    @Test
    void falloEnDeduplicacionReencolaSinPerderElMensaje() throws Exception {
        var puertos = new Puertos();
        when(puertos.persistencia.existeRegistro(any())).thenThrow(new IllegalStateException("db"));
        when(puertos.canal.basicGet(ColaReintentoAuditoriaRabbit.COLA, false))
                .thenReturn(puertos.mensaje(String.format(CUERPO, UUID.randomUUID()), 0));

        puertos.worker.reintentar();

        verify(puertos.canal).basicNack(7L, false, true);
        verify(puertos.canal, never()).basicAck(7L, false);
        verify(puertos.canal, never()).basicReject(7L, false);
    }

    @Test
    void falloEnRepublicacionReencolaSinPerderElMensaje() throws Exception {
        var puertos = new Puertos();
        when(puertos.persistencia.existeRegistro(any())).thenReturn(false);
        doThrow(new IllegalStateException("insert")).when(puertos.persistencia).guardar(any(RegistroAuditoria.class));
        doThrow(new java.io.IOException("broker")).when(puertos.canal)
                .basicPublish(any(), any(), any(), any());
        when(puertos.canal.basicGet(ColaReintentoAuditoriaRabbit.COLA, false))
                .thenReturn(puertos.mensaje(String.format(CUERPO, UUID.randomUUID()), 0));
        Duration[] esperas = ReintentoAuditoriaWorker.ESPERAS.clone();
        ReintentoAuditoriaWorker.ESPERAS[0] = Duration.ZERO;
        try {
            puertos.worker.reintentar();
        } finally {
            ReintentoAuditoriaWorker.ESPERAS[0] = esperas[0];
        }

        verify(puertos.canal).basicNack(7L, false, true);
        verify(puertos.canal, never()).basicAck(7L, false);
        verify(puertos.canal, never()).basicReject(7L, false);
    }
}
