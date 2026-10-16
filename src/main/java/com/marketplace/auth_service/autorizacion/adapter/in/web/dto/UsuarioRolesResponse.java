package com.marketplace.auth_service.autorizacion.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Respuesta 200 de la asignación: esquema {@code Usuario} del contrato con sus
 * campos obligatorios ({@code id}, {@code nombreCompleto}, {@code correo},
 * {@code estado}, {@code roles}). Los opcionales se omiten: la pantalla
 * recarga el detalle completo de Eva después del cambio (FRONT-08).
 */
public record UsuarioRolesResponse(UUID id, String nombreCompleto, String correo, String estado,
                                   List<String> roles) {
}
