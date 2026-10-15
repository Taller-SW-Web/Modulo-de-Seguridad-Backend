package com.marketplace.auth_service.usuario.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Esquema {@code Usuario} del contrato para administración. {@code celular}
 * se omite si no está registrado. Atributos de perfil, documento enmascarado y
 * {@code bloqueo} los aportan sus dueños y se añadirán al componerlos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioResponse(
        UUID id, String nombres, String apellidos, String nombreCompleto, String correo, String celular,
        String estado, boolean correoVerificado, boolean celularVerificado, boolean mfaHabilitado,
        List<String> roles) {
}
