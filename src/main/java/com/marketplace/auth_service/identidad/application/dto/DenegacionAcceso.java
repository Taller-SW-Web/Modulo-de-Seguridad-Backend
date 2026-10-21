package com.marketplace.auth_service.identidad.application.dto;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Petición autenticada que recibió 403 {@code SCOPE_INSUFICIENTE} (SPEC-17
 * ESC-17.8). Se audita quién y qué ruta; nunca el token ni el scope que faltaba.
 *
 * @param actorTipo {@code MODULO} para un token de servicio, {@code USUARIO} para uno de persona
 * @param actorId   {@code client_id} del módulo o {@code sub} de la persona
 * @param jti       identificador del token presentado (ESC-12.3)
 */
public record DenegacionAcceso(String actorTipo, String actorId, UUID jti, String metodo, String ruta,
                               ContextoPeticion contexto, Instant fecha) {

    public static final String MODULO = "MODULO";
    public static final String USUARIO = "USUARIO";

    public DenegacionAcceso {
        Objects.requireNonNull(actorTipo, "actorTipo");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(metodo, "metodo");
        Objects.requireNonNull(ruta, "ruta");
        Objects.requireNonNull(contexto, "contexto");
        Objects.requireNonNull(fecha, "fecha");
    }
}
