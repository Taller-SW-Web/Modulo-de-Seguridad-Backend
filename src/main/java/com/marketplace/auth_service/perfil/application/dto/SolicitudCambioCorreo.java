package com.marketplace.auth_service.perfil.application.dto;

/** Cuerpo del cambio de correo (SPEC-16, RF-16.6). Lleva una contraseña: nunca se imprime. */
public record SolicitudCambioCorreo(String nuevoCorreo, String contrasenaActual) {
    @Override
    public String toString() {
        return "SolicitudCambioCorreo[REDACTADO]";
    }
}
