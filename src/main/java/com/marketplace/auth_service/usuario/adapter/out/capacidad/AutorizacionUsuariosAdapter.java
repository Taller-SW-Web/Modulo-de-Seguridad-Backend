package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.dto.RolesDeCuenta;
import com.marketplace.auth_service.usuario.application.port.out.AutorizacionUsuariosPort;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Puente a {@code AutorizacionPort} (Sergio): permiso actual y roles de una página en lote. */
public final class AutorizacionUsuariosAdapter implements AutorizacionUsuariosPort {

    private final AutorizacionPort autorizacion;

    public AutorizacionUsuariosAdapter(AutorizacionPort autorizacion) {
        this.autorizacion = Objects.requireNonNull(autorizacion, "autorizacion");
    }

    @Override
    public void exigirVerUsuarios(ActorUsuario actor) {
        autorizacion.exigirPermiso(actor, Permiso.USUARIO_VER);
    }

    @Override
    public Map<UUID, RolesDeCuenta> rolesDe(Set<UUID> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, RolesDeCuenta> resultado = new HashMap<>();
        autorizacion.rolesDe(usuarioIds).forEach((id, roles) -> resultado.put(id, new RolesDeCuenta(
                // Orden canónico del catálogo, estable entre peticiones.
                roles.stream().sorted().map(Rol::name).toList(),
                roles.stream().anyMatch(Rol::esDeGestion))));
        return resultado;
    }
}
