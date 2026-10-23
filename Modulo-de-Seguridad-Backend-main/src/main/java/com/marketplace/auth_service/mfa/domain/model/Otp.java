package com.marketplace.auth_service.mfa.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Representación del OTP de LOGIN_MFA: solo hash, contexto y estado. */
public record Otp(ContextoOtp contexto, String hashCodigo, String hashKeyId, CanalOtp canal,
                  String destino, Instant creadoEn, Instant expiraEn, boolean usado,
                  int intentos, Instant invalidadoEn) {
    public static final Duration VIGENCIA = Duration.ofSeconds(300);
    public static final int MAX_INTENTOS = 3;

    public Otp {
        Objects.requireNonNull(contexto, "contexto");
        Objects.requireNonNull(canal, "canal");
        Objects.requireNonNull(creadoEn, "creadoEn");
        Objects.requireNonNull(expiraEn, "expiraEn");
        if (hashCodigo == null || !hashCodigo.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("hashCodigo debe ser HMAC-SHA-256 hexadecimal");
        }
        if (hashKeyId == null || hashKeyId.isBlank() || hashKeyId.length() > 50) {
            throw new IllegalArgumentException("hashKeyId es obligatorio y admite hasta 50 caracteres");
        }
        if (destino == null || destino.isBlank()) {
            throw new IllegalArgumentException("destino es obligatorio");
        }
        if (!expiraEn.isAfter(creadoEn) || intentos < 0 || intentos > MAX_INTENTOS
                || (invalidadoEn != null && invalidadoEn.isBefore(creadoEn))) {
            throw new IllegalArgumentException("estado temporal o intentos del OTP inválidos");
        }
    }

    public static Otp nuevo(ContextoOtp contexto, String hashCodigo, String hashKeyId,
                            CanalOtp canal, String destino, Instant ahora) {
        Objects.requireNonNull(ahora, "ahora");
        return new Otp(contexto, hashCodigo, hashKeyId, canal, destino,
                ahora, ahora.plus(VIGENCIA), false, 0, null);
    }

    public boolean vigenteEn(Instant ahora) {
        Objects.requireNonNull(ahora, "ahora");
        return !usado && invalidadoEn == null && intentos < MAX_INTENTOS
                && !ahora.isBefore(creadoEn) && ahora.isBefore(expiraEn);
    }

    @Override
    public String toString() {
        return "Otp[id=" + contexto.otpId() + ", canal=" + canal + ", usado=" + usado
                + ", intentos=" + intentos + ", expiraEn=" + expiraEn + "]";
    }
}
