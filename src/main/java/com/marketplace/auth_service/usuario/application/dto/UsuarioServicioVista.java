package com.marketplace.auth_service.usuario.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Proyección de usuario para un módulo consumidor (esquema {@code Usuario}
 * del contrato, SPEC-18 RF-18.1/RF-18.4), tal como la entrega el puente a
 * identidad. {@code documento} (en claro) y {@code documentoEnmascarado} son
 * mutuamente excluyentes; ambos {@code null} si no hay documento registrado.
 */
public record UsuarioServicioVista(
        UUID id, String nombres, String apellidos, String nombreCompleto, String correo, String celular,
        String estado, boolean correoVerificado, boolean celularVerificado, List<String> roles,
        String tipoDocumento, String documento, String documentoEnmascarado) {

    public UsuarioServicioVista {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(estado, "estado");
        roles = List.copyOf(roles);
    }
}
