package com.marketplace.auth_service.auditoria.domain.model;

import com.marketplace.auth_service.auditoria.domain.exception.AccionNoCatalogadaException;
import com.marketplace.auth_service.auditoria.domain.exception.DetalleInseguroException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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

    /**
     * Fragmentos prohibidos en la clave del detalle (RF-12.5), tras pasarla a
     * minúsculas sin separadores: basta con que la clave los contenga, así
     * {@code contrasenaNueva}, {@code refresh_token} o {@code numeroDocumento}
     * también se rechazan sin depender de cómo nombre cada productor.
     */
    private static final List<String> FRAGMENTOS_PROHIBIDOS = List.of(
            "contrasena", "password", "passwd", "secret", "hash", "otp", "token",
            "documento", "cookie", "authorization", "bearer");

    /** Claves prohibidas solo si coinciden exactas (son demasiado genéricas como fragmento). */
    private static final Set<String> CLAVES_PROHIBIDAS = Set.of("codigo", "clave", "pin");

    /** Referencias permitidas aunque contengan un fragmento: el jti del token (ESC-12.3) y el tipo de documento. */
    private static final Set<String> CLAVES_PERMITIDAS = Set.of("jti", "tokenjti", "tipodocumento");

    /** Valores con forma de JWT o de hash de contraseña (bcrypt/argon2), venga en la clave que venga. */
    private static final Pattern VALOR_SECRETO = Pattern.compile(
            "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.|\\$2[aby]?\\$\\d{2}\\$|\\$argon2(id|i|d)\\$");

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
        for (Map.Entry<String, String> entrada : detalle.entrySet()) {
            String clave = entrada.getKey();
            if (claveProhibida(clave)
                    || (entrada.getValue() != null && VALOR_SECRETO.matcher(entrada.getValue()).find())) {
                throw new DetalleInseguroException(clave);
            }
        }
        return detalle;
    }

    private static boolean claveProhibida(String clave) {
        if (clave == null) {
            return true;
        }
        String normalizada = clave.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (CLAVES_PERMITIDAS.contains(normalizada)) {
            return false;
        }
        return CLAVES_PROHIBIDAS.contains(normalizada)
                || FRAGMENTOS_PROHIBIDOS.stream().anyMatch(normalizada::contains);
    }

    @Override
    public String toString() {
        return "RegistroAuditoria[registroId=" + registroId + ", accion=" + accion
                + ", resultado=" + resultado + ", REDACTADO]";
    }
}
