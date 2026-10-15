package com.marketplace.auth_service.credencial.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class TokenUnUsoHashing {
    private TokenUnUsoHashing() { }
    public static String sha256Hex(String tokenClaro) {
        Objects.requireNonNull(tokenClaro, "tokenClaro");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(tokenClaro.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no está disponible", impossible);
        }
    }
}
