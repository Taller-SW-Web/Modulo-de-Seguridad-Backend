package com.marketplace.auth_service.identidad.adapter.in.web.dto;

/** Cuerpo de {@code POST /auth/introspeccion}. {@code toString} no incluye el token. */
public record IntrospeccionRequest(String token) {

    @Override
    public String toString() {
        return "IntrospeccionRequest[token=REDACTADO]";
    }
}
