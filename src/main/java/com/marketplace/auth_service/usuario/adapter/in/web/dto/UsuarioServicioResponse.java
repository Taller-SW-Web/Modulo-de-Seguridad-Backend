package com.marketplace.auth_service.usuario.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Esquema {@code Usuario} del contrato para un módulo consumidor (SPEC-18
 * RF-18.1/RF-18.4): sin {@code mfaHabilitado} ni atributos de perfil, privados
 * del titular y la administración. {@code documento} y {@code documentoEnmascarado}
 * son mutuamente excluyentes; ambos se omiten si no hay documento registrado.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioServicioResponse(
        UUID id, String nombres, String apellidos, String nombreCompleto, String correo, String celular,
        String estado, boolean correoVerificado, boolean celularVerificado, List<String> roles,
        String tipoDocumento, String documento, String documentoEnmascarado) {
}
