package com.marketplace.auth_service.usuario.application.dto;

import java.util.UUID;

/**
 * Identidad, estado y contadores propios. mfaHabilitado es la bandera voluntaria
 * persistida; el efectivo requiere los roles del dueño de autorización.
 * No contiene credenciales, documento ni proyecciones ajenas.
 */
public record CuentaSnapshot(
        UUID id, String nombres, String apellidos, String correo, String celular,
        String estadoBase, String estadoEfectivo, boolean correoVerificado,
        boolean celularVerificado, boolean mfaHabilitado,
        int bloqueosSeguidos, int intentosFallidosConsecutivos) {
    public String nombreCompleto() { return nombres + " " + apellidos; }
}
