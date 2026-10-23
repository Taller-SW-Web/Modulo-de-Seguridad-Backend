package com.marketplace.auth_service.autorizacion.application.dto;

import com.marketplace.auth_service.shared.domain.Rol;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Hecho {@code usuario.roles_cambiados} (catálogo de eventos). Lleva la
 * <b>lista completa resultante</b>, no el cambio: un consumidor que reciba dos
 * eventos desordenados se queda con el último estado. El puente hacia
 * {@code MensajeriaPort} (Christian) lo guarda en el outbox en el mismo commit;
 * se publica después.
 *
 * @param eventoId  identificador del evento
 * @param usuarioId cuenta cuyos roles cambiaron
 * @param roles     roles resultantes, en el orden del catálogo
 * @param fecha     momento del cambio
 */
public record RolesCambiadosEvento(UUID eventoId, UUID usuarioId, List<Rol> roles, Instant fecha) {

    public static final String TIPO = "usuario.roles_cambiados";

    public RolesCambiadosEvento {
        Objects.requireNonNull(eventoId, "eventoId");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(fecha, "fecha");
        roles = List.copyOf(roles);
    }
}
