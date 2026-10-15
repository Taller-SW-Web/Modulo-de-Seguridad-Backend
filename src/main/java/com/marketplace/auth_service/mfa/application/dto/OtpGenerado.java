package com.marketplace.auth_service.mfa.application.dto;

import java.util.Objects;

/** Código efímero para el productor de correo; solamente hash y key id van a la tabla OTP. */
public record OtpGenerado(String codigo, String hashCodigo, String hashKeyId) {
    public OtpGenerado {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(hashCodigo, "hashCodigo");
        Objects.requireNonNull(hashKeyId, "hashKeyId");
    }

    @Override
    public String toString() {
        return "OtpGenerado[codigo=REDACTADO, hashCodigo=REDACTADO, hashKeyId=" + hashKeyId + "]";
    }
}
