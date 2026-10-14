package com.marketplace.auth_service.autorizacion.adapter.in.transaction;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Frontera transaccional de {@link AutorizacionPort} (BACKEND-HEXAGONAL §4).
 * El caso de uso puro no sabe de transacciones; este decorador sí. Es el único
 * bean {@code AutorizacionPort}: nadie puede llamar al servicio sin pasar por aquí.
 *
 * <ul>
 *   <li>Lecturas: {@code readOnly}; si el llamador ya tiene una transacción, se unen a ella.</li>
 *   <li>{@link #bloquearCatalogoAdministrador()}: {@code MANDATORY}. Un lock fuera de
 *       la transacción del caso de uso se soltaría al instante y no protegería nada,
 *       así que sin transacción abierta falla en vez de fingir que bloquea.</li>
 * </ul>
 */
public class AutorizacionTransaccional implements AutorizacionPort {

    private final AutorizacionPort casoDeUso;

    public AutorizacionTransaccional(AutorizacionPort casoDeUso) {
        this.casoDeUso = casoDeUso;
    }

    @Override
    @Transactional(readOnly = true)
    public PermisosResueltos resolver(UUID usuarioId) {
        return casoDeUso.resolver(usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public void exigirPermiso(ActorUsuario actor, Permiso permiso) {
        casoDeUso.exigirPermiso(actor, permiso);
    }

    @Override
    @Transactional(readOnly = true)
    public void exigirTitularOPrivilegio(ActorUsuario actor, UUID usuarioObjetivo, Permiso permiso) {
        casoDeUso.exigirTitularOPrivilegio(actor, usuarioObjetivo, permiso);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void bloquearCatalogoAdministrador() {
        casoDeUso.bloquearCatalogoAdministrador();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> usuariosConRol(Rol rol) {
        return casoDeUso.usuariosConRol(rol);
    }
}
