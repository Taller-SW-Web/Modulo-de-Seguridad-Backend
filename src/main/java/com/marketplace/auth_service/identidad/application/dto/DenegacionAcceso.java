package com.marketplace.auth_service.identidad.application.dto;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Petición rechazada con 403 en la frontera HTTP (SPEC-17 ESC-17.4 y ESC-17.8).
 * Se audita quién, qué ruta y por qué; nunca el token ni el scope que faltaba.
 *
 * @param motivo    {@code SCOPE_INSUFICIENTE} o {@code TOKEN_NO_APLICABLE}
 * @param actorTipo {@code MODULO} para un token de servicio, {@code USUARIO} para uno de persona
 * @param actorId   {@code client_id} del módulo o {@code sub} de la persona
 * @param jti       identificador del token presentado (ESC-12.3); puede faltar
 */
public record DenegacionAcceso(String motivo, String actorTipo, String actorId, UUID jti, String metodo,
                               String ruta, ContextoPeticion contexto, Instant fecha) {

    public static final String SCOPE_INSUFICIENTE = "SCOPE_INSUFICIENTE";
    public static final String TOKEN_NO_APLICABLE = "TOKEN_NO_APLICABLE";
    public static final String MODULO = "MODULO";
    public static final String USUARIO = "USUARIO";

    public DenegacionAcceso {
        Objects.requireNonNull(motivo, "motivo");
        Objects.requireNonNull(actorTipo, "actorTipo");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(metodo, "metodo");
        Objects.requireNonNull(ruta, "ruta");
        Objects.requireNonNull(contexto, "contexto");
        Objects.requireNonNull(fecha, "fecha");
    }
}
