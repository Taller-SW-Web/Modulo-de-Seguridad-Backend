package com.marketplace.auth_service.perfil.adapter.in.web;

/**
 * La petición intenta editar la cuenta de otra persona o un atributo que el
 * titular no puede cambiar en H3. Se traduce a 403 {@code SCOPE_INSUFICIENTE}
 * sin decir cuál fue el motivo ni mostrar valores.
 */
final class EdicionNoPermitidaException extends RuntimeException {
    EdicionNoPermitidaException() {
        super("Edición no permitida");
    }
}
