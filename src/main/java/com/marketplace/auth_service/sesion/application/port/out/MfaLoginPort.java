package com.marketplace.auth_service.sesion.application.port.out;

import com.marketplace.auth_service.sesion.application.dto.DesafioCreado;
import com.marketplace.auth_service.sesion.application.dto.RevisionCredencial;
import com.marketplace.auth_service.shared.domain.Rol;

import java.util.Set;
import java.util.UUID;

/**
 * Segundo factor para el login (Luis, SPEC-09/10): MFA efectivo (voluntario o
 * exigido por rol de gestión) y creación del desafío con la revisión de
 * credencial vigente. La verificación y el canje son suyos, no de este puerto.
 */
public interface MfaLoginPort {
    boolean esEfectivo(boolean mfaHabilitado, Set<Rol> roles);
    DesafioCreado crearDesafio(UUID usuarioId, RevisionCredencial revision);
}
