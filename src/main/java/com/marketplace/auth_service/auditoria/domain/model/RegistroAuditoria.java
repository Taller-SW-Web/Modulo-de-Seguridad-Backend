package com.marketplace.auth_service.auditoria.domain.model;

import com.marketplace.auth_service.auditoria.domain.exception.AccionNoCatalogadaException;
import com.marketplace.auth_service.auditoria.domain.exception.DetalleInseguroException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Hecho de seguridad para el registro central (SPEC-12, RF-12.1–12.5).
 * Guarda el hecho, nunca el dato: sin contraseñas, hashes, OTP, tokens
 * completos ni documento en claro. Un token se referencia solo por su jti
 * (ESC-12.3). Núcleo puro, sin Spring/JPA/HTTP.
 */
public record RegistroAuditoria(UUID registroId, AccionAuditable accion, Resultado resultado,
                                ActorTipo actorTipo, String actorId, UUID objetivoUsuarioId,
                                Instant fecha, String ip, String agente,
                                Map<String, String> detalle) {

    /** Claves de detalle prohibidas por RF-12.5 (comparación insensible a mayúsculas). */
    private static final Set<String> CLAVES_PROHIBIDAS = Set.of(
            "contrasena", "password", "hash", "hashcontrasena",
            "otp", "codigo", "codigootp", "token", "documento");

    public RegistroAuditoria {
        Objects.requireNonNull(registroId, "registroId");
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(actorTipo, "actorTipo");
        Objects.requireNonNull(fecha, "fecha");
        detalle = detalle == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(validarDetalle(detalle)));
    }

    /**
     * Crea un registro validando catálogo y detalle seguro.
     *
     * @throws AccionNoCatalogadaException si el código no está en SPEC-12 (RF-12.2)
     * @throws DetalleInseguroException si el detalle trae un secreto (RF-12.5)
     */
    public static RegistroAuditoria nuevo(UUID registroId, String codigoAccion, Resultado resultado,
                                          ActorTipo actorTipo, String actorId, UUID objetivoUsuarioId,
                                          Instant fecha, String ip, String agente,
                                          Map<String, String> detalle) {
        AccionAuditable accion;
        try {
            accion = AccionAuditable.valueOf(codigoAccion);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AccionNoCatalogadaException(codigoAccion);
        }
        return new RegistroAuditoria(registroId, accion, resultado, actorTipo, actorId,
                objetivoUsuarioId, fecha, ip, agente, detalle);
    }

    private static Map<String, String> validarDetalle(Map<String, String> detalle) {
        for (String clave : detalle.keySet()) {
            String normalizada = clave == null ? ""
                    : clave.toLowerCase(Locale.ROOT).replace("_", "");
            if (CLAVES_PROHIBIDAS.contains(normalizada)
                    || normalizada.contains("password")
                    || normalizada.contains("secret")) {
                throw new DetalleInseguroException(clave);
            }
        }
        return detalle;
    }

    @Override
    public String toString() {
        return "RegistroAuditoria[registroId=" + registroId + ", accion=" + accion
                + ", resultado=" + resultado + ", REDACTADO]";
    }
}
