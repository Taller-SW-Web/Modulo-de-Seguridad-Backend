package com.marketplace.auth_service.usuario.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Esquema {@code Usuario} del contrato para administración. {@code celular}
 * se omite si no está registrado y {@code bloqueo} si la cuenta no está bloqueada
 * (RF-15.6). Atributos de perfil y documento enmascarado los aportan sus dueños
 * y se añadirán al componerlos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioResponse(
        UUID id, String nombres, String apellidos, String nombreCompleto, String correo, String celular,
        String estado, boolean correoVerificado, boolean celularVerificado, boolean mfaHabilitado,
        List<String> roles, BloqueoResponse bloqueo) {

    /** {@code hasta} se emite como {@code null} si no vence; {@code motivo} solo en bloqueo manual. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BloqueoResponse(
            String tipo,
            @JsonInclude(JsonInclude.Include.ALWAYS) Instant hasta,
            String motivo) {
    }
}
