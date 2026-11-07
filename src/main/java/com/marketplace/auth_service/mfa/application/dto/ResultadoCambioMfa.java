package com.marketplace.auth_service.mfa.application.dto;

public sealed interface ResultadoCambioMfa {
    record Enviado(String mfaChangeToken, String canal, String destinoEnmascarado,
            int expiraEn, int cambioExpiraEn) implements ResultadoCambioMfa {
        @Override public String toString() { return "Enviado[token=REDACTADO, canal=" + canal + "]"; }
    }
    record Error(String code, Integer intentosRestantes, Long retryAfter) implements ResultadoCambioMfa { }
    enum Confirmado implements ResultadoCambioMfa { EXITO }
    static Error error(String code) { return new Error(code, null, null); }
}
