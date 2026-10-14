package com.marketplace.auth_service.autorizacion.adapter.in.transaction;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Frontera transaccional de {@link AutorizacionPort} (BACKEND-HEXAGONAL §4).
 * El caso de uso puro no sabe de transacciones; este decorador sí. Es el único
 * bean {@code AutorizacionPort}: nadie puede llamar al servicio sin pasar por aquí.
 *
 * <p>Las lecturas usan {@code readOnly}: si el llamador (p. ej. el login) ya
 * tiene una transacción abierta, se unen a ella.
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
}
