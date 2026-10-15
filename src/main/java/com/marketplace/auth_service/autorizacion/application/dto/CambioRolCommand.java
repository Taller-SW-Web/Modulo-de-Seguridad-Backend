package com.marketplace.auth_service.autorizacion.application.dto;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.util.Objects;
import java.util.UUID;

/**
 * Pedido de asignar o revocar <b>un</b> rol (RF-11.3: de uno en uno, nunca la lista entera).
 *
 * <p>{@code rol} llega como texto sin validar a propósito: primero se comprueba
 * que el actor puede cambiar roles y solo después se valida el código, así
 * alguien sin permiso recibe 403 aunque el código sea inválido.
 *
 * @param actor           quien pide el cambio, ya verificado por el filtro de acceso
 * @param usuarioObjetivo cuenta cuyos roles cambian
 * @param rol             código de rol recibido
 * @param contexto        IP y correlación para la auditoría
 */
public record CambioRolCommand(ActorUsuario actor, UUID usuarioObjetivo, String rol, ContextoPeticion contexto) {

    public CambioRolCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(usuarioObjetivo, "usuarioObjetivo");
        Objects.requireNonNull(contexto, "contexto");
    }
}
