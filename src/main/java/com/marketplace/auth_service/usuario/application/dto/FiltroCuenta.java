package com.marketplace.auth_service.usuario.application.dto;

public record FiltroCuenta(
    String estado,
    String rol,
    String correoFragmento
) {}