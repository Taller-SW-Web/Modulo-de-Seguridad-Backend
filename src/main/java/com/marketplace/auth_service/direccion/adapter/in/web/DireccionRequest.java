package com.marketplace.auth_service.direccion.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DireccionRequest(
        @Size(max=50) String etiqueta,
        @NotBlank @Size(max=100) String departamento,
        @NotBlank @Size(max=100) String provincia,
        @NotBlank @Size(max=100) String distrito,
        @NotBlank @Size(max=255) String direccion,
        @Size(max=255) String referencia,
        Boolean esPredeterminada) {}
