package com.marketplace.auth_service.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.auditoria.adapter.in.worker.PublicacionEventosWorker;
import com.marketplace.auth_service.auditoria.adapter.out.broker.PublicadorEventosRabbit;
import com.marketplace.auth_service.auditoria.application.port.out.PublicadorEventosPort;
import com.marketplace.auth_service.auditoria.application.service.MensajeriaService;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publicación de eventos en RabbitMQ (TEC-INFRA-T004), solo con {@code G7_EVENTOS_PUBLICAR=true} y
 * el broker en {@code SPRING_RABBITMQ_*} (o {@code SPRING_RABBITMQ_ADDRESSES} para un servicio en
 * la nube). Sin ellos los eventos quedan PENDIENTE en el outbox y nada se pierde.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "g7.eventos.publicar", havingValue = "true")
public class EventosConfig {

    /** Exchange del catálogo; RabbitAdmin lo declara al conectar (topic, duradero, sin borrado automático). */
    @Bean
    TopicExchange exchangeSeguridadUsuarios() {
        return new TopicExchange(PublicadorEventosRabbit.EXCHANGE, true, false);
    }

    @Bean
    PublicadorEventosPort publicadorEventos(RabbitTemplate rabbit, ObjectMapper json) {
        return new PublicadorEventosRabbit(rabbit, json);
    }

    @Bean
    PublicacionEventosWorker publicacionEventosWorker(MensajeriaService mensajeria, PublicadorEventosPort publicador) {
        return new PublicacionEventosWorker(mensajeria, publicador);
    }
}
