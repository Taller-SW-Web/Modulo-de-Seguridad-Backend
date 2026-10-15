package com.marketplace.auth_service.usuario.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuenta proyectada para administración (esquema {@code Usuario} del contrato,
 * vista con {@code usuario.ver}). Sin credenciales, documento ni contadores.
 *
 * @param estado        estado <b>efectivo</b>: un bloqueo automático vencido ya es {@code ACTIVO}
 * @param mfaHabilitado efectivo: bandera voluntaria o cualquier rol de gestión (RF-10.1)
 * @param roles         códigos del catálogo, en su orden canónico
 */
public record UsuarioVista(
        UUID id, String nombres, String apellidos, String nombreCompleto, String correo, String celular,
        String estado, boolean correoVerificado, boolean celularVerificado, boolean mfaHabilitado,
        List<String> roles) {

    public UsuarioVista {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(estado, "estado");
        roles = List.copyOf(roles);
    }
}
