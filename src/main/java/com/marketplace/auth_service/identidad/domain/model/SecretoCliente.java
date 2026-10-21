package com.marketplace.auth_service.identidad.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Hash del {@code client_secret} (SPEC-17: el secreto siempre se guarda hasheado).
 *
 * <p>Los secretos de servicio los genera operaciones con al menos 256 bits
 * aleatorios, así que basta SHA-256 sin sal: no hay diccionario que probar y el
 * token endpoint no paga el coste de Argon2 en cada petición. La comparación es
 * en tiempo constante. Se guarda como 64 hex en minúsculas.
 */
public final class SecretoCliente {

    /** Hash de un cliente inexistente: se compara igual para no delatar si existe. */
    public static final String HASH_SENUELO = "0".repeat(64);

    private SecretoCliente() {
    }

    public static String hash(String secreto) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secreto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** ¿El secreto presentado corresponde al hash guardado? Siempre en tiempo constante. */
    public static boolean coincide(String secreto, String hashGuardado) {
        byte[] calculado = hash(secreto).getBytes(StandardCharsets.US_ASCII);
        byte[] guardado = hashGuardado.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(calculado, guardado);
    }
}
