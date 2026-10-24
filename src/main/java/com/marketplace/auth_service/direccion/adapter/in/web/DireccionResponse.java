package com.marketplace.auth_service.direccion.adapter.in.web;

import java.util.UUID;

public record DireccionResponse(UUID id, String etiqueta, String departamento, String provincia,
        String distrito, String direccion, String referencia, boolean esPredeterminada) {}
