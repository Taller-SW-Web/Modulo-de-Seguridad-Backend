package com.marketplace.auth_service.identidad.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Proyección de usuario para un módulo consumidor, tal como la define el
 * esquema {@code Usuario} del contrato (SPEC-18 RF-18.1/RF-18.4): sin
 * {@code mfaHabilitado} ni atributos de perfil, que son privados del titular
 * y la administración. {@code documento} es el número en claro (solo con
 * {@code usuarios:leer:documento}); si no, {@code documentoEnmascarado}. Los
 * dos son mutuamente excluyentes, y ambos {@code null} si no hay documento
 * registrado.
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
