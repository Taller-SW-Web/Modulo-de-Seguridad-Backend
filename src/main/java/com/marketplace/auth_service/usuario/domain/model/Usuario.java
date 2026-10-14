package com.marketplace.auth_service.usuario.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Usuario(
    UUID id,
    String nombres,
    String apellidos,
    String correo,
    String celular,
    EstadoCuenta estadoBase,
    EstadoCuenta estadoEfectivo,
    boolean correoVerificado,
    boolean mfaHabilitado,
    OffsetDateTime fechaNacimiento,
    String tipoDocumento,
    String documentoHash,
    String documentoHmac,
    int bloqueosSeguidos,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }
}