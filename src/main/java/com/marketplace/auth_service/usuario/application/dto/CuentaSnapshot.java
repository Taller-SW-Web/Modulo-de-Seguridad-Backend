package com.marketplace.auth_service.usuario.application.dto;

import java.util.Set;
import java.util.UUID;

public record CuentaSnapshot(
    UUID id,
    String nombres,
    String apellidos,
    String correo,
    String celular,
    String estadoBase,
    String estadoEfectivo,
    boolean correoVerificado,
    boolean mfaHabilitado,
    int bloqueosSeguidos,
    String tipoDocumento,
    String documentoEnmascarado,
    Set<String> roles
) {
    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }
}