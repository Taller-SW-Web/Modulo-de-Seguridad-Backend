package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;

/**
 * Refresco recién generado: 32 bytes aleatorios en base64url sin padding y su
 * SHA-256 hexadecimal para persistir. El secreto viaja una vez y nunca sale a
 * logs; el hash es lo único que se guarda. Sin secreto en {@code toString}.
 */
public record RefrescoGenerado(String secreto, String hash) {
    public RefrescoGenerado {
        Objects.requireNonNull(secreto, "secreto");
        if (hash == null || !hash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("hash debe ser SHA-256 hexadecimal");
        }
    }

    @Override
    public String toString() {
        return "RefrescoGenerado[hash=" + hash + "]";
    }
}
