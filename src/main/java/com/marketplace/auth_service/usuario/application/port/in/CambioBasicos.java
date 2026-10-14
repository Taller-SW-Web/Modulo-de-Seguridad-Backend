package com.marketplace.auth_service.usuario.application.port.in;

public record CambioBasicos(
    String nombres,
    String apellidos,
    String celular,
    String tipoDocumento,
    String numeroDocumento
) {}