package com.marketplace.auth_service.sesion.adapter.out.capacidad;

import com.marketplace.auth_service.sesion.application.dto.DesafioCreado;
import com.marketplace.auth_service.sesion.application.dto.RevisionCredencial;
import com.marketplace.auth_service.sesion.application.port.out.MfaLoginPort;
import com.marketplace.auth_service.sesion.domain.exception.DependenciaNoDisponibleException;
import com.marketplace.auth_service.shared.domain.Rol;

import java.util.Set;
import java.util.UUID;

/**
 * Marcador hasta que Luis entregue {@code MfaPort}: MFA efectivo y desafíos
 * son suyos (SPEC-09/10). La verificación y el canje del OTP también; aquí
 * solo se pide crear el desafío tras contraseña válida. Reemplazo en T007.
 */
public class MfaLoginPendiente implements MfaLoginPort {

    @Override
    public boolean esEfectivo(boolean mfaHabilitado, Set<Rol> roles) {
        throw new DependenciaNoDisponibleException("MfaPort de Luis");
    }

    @Override
    public DesafioCreado crearDesafio(UUID usuarioId, RevisionCredencial revision) {
        throw new DependenciaNoDisponibleException("MfaPort de Luis");
    }
}
