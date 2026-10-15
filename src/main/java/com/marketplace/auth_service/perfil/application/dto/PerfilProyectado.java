package com.marketplace.auth_service.perfil.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Proyección contractual del perfil (Usuario/PerfilUsuario). Nunca contiene documento en claro
 * ni cifrado, hashes, OTP ni tokens. atributosCliente/atributosVendedor/documento son null
 * cuando no aplican o no existen; mfaHabilitado es el estado efectivo.
 */
public record PerfilProyectado(
        UUID id,
        String nombres,
        String apellidos,
        String nombreCompleto,
        String correo,
        String celular,
        String estado,
        boolean correoVerificado,
        boolean celularVerificado,
        boolean mfaHabilitado,
        List<String> roles,
        AtributosCliente atributosCliente,
        AtributosVendedor atributosVendedor,
        DocumentoProyectado documento) {
    public PerfilProyectado {
        Objects.requireNonNull(id, "id");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
    }

    /** Sin datos personales: evita fugas por logs. */
    @Override
    public String toString() {
        return "PerfilProyectado[id=" + id + "]";
    }
}
