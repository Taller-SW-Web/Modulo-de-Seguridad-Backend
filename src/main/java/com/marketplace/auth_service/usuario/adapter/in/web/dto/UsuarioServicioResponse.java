package com.marketplace.auth_service.usuario.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Esquema de usuario para un módulo consumidor (SPEC-18 RF-18.1/RF-18.4).
 * {@code documento} se omite si el cliente no tiene uno registrado.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioServicioResponse(
        UUID id, String nombreCompleto, String correo, String telefono, String estado,
        List<String> roles, DocumentoResponse documento) {

    public record DocumentoResponse(String valor, boolean enClaro) {
    }
}
