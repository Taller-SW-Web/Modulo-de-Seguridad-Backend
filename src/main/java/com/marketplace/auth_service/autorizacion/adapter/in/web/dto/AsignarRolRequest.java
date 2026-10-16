package com.marketplace.auth_service.autorizacion.adapter.in.web.dto;

/**
 * Cuerpo de POST {@code /usuarios/{id}/roles}: un único rol. El código se
 * recibe como texto y lo valida el caso de uso <b>después</b> de comprobar el
 * permiso. El cuerpo no decide el actor: cualquier otro campo se ignora.
 */
public record AsignarRolRequest(String rol) {
}
