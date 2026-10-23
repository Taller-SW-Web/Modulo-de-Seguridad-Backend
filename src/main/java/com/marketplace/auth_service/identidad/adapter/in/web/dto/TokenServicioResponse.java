package com.marketplace.auth_service.identidad.adapter.in.web.dto;

import java.util.List;

/** Esquema {@code TokenServicio} del contrato. {@code toString} no incluye el token. */
public record TokenServicioResponse(String accessToken, String tokenType, long expiresIn, List<String> scopes) {

    @Override
    public String toString() {
        return "TokenServicioResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", scopes=" + scopes + "]";
    }
}
