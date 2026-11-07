package com.marketplace.auth_service.perfil.domain.model;

/** El actor no tiene {@code atributos.editar}: 403 {@code SCOPE_INSUFICIENTE}, sin efecto alguno. */
public class EdicionAdministrativaNoPermitidaException extends RuntimeException {
    public EdicionAdministrativaNoPermitidaException() {
        super("Edición administrativa no permitida");
    }
}
