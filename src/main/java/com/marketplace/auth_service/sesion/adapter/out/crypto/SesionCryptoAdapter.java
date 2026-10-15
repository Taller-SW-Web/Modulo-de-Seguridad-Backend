package com.marketplace.auth_service.sesion.adapter.out.crypto;

import com.marketplace.auth_service.sesion.application.dto.RefrescoGenerado;
import com.marketplace.auth_service.sesion.application.port.out.CriptografiaSesionPort;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Refrescos opacos (SPEC-06): 32 bytes de {@code SecureRandom} en base64url sin
 * padding y SHA-256 hexadecimal para persistir. Sin proveedor externo: la
 * aleatoriedad y el hash son Java estándar.
 */
public class SesionCryptoAdapter implements CriptografiaSesionPort {

    private final SecureRandom aleatorio;

    public SesionCryptoAdapter() {
        this(new SecureRandom());
    }

    SesionCryptoAdapter(SecureRandom aleatorio) {
        this.aleatorio = aleatorio;
    }

    @Override
    public RefrescoGenerado generarRefresco() {
        byte[] secreto = new byte[32];
        aleatorio.nextBytes(secreto);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(secreto);
        return new RefrescoGenerado(valor, sha256Hex(valor));
    }

    /** SHA-256 hexadecimal de un valor, para contrastar lo persistido en pruebas. */
    public static String sha256Hex(String valor) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var hex = new StringBuilder(64);
            for (byte b : resumen) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
