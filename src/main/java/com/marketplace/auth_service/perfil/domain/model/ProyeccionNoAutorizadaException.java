package com.marketplace.auth_service.perfil.domain.model;

/** El actor no es titular ni tiene el privilegio requerido. Se traduce a 403 en el adaptador HTTP. */
public class ProyeccionNoAutorizadaException extends RuntimeException {
    public ProyeccionNoAutorizadaException() {
        super("Proyección de perfil no autorizada");
    }
}
