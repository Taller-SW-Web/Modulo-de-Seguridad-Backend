package com.marketplace.auth_service.usuario.application.dto;

import com.marketplace.auth_service.usuario.domain.model.CodigoRol;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.util.Set;
import java.util.UUID;

public record CuentaSnapshot(
    UUID id,
    String nombres,
    String apellidos,
    String correo,
    String celular,
    EstadoCuenta estadoBase,
    EstadoCuenta estadoEfectivo,
    boolean correoVerificado,
    boolean mfaHabilitado,
    int bloqueosSeguidos,
    String tipoDocumento,
    String documentoEnmascarado,
    Set<CodigoRol> roles
) {
    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }
}