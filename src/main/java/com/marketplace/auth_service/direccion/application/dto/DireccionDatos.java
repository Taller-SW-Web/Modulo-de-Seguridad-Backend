package com.marketplace.auth_service.direccion.application.dto;

import java.util.UUID;

/** DTO público de la capacidad de direcciones; no expone entidades de persistencia. */
public record DireccionDatos(UUID id, String etiqueta, String departamento, String provincia,
        String distrito, String direccion, String referencia, boolean esPredeterminada) {}
