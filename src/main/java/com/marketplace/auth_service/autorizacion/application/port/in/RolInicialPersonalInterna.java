package com.marketplace.auth_service.autorizacion.application.port.in;

import com.marketplace.auth_service.shared.domain.Rol;
import java.util.UUID;

/**
 * Rol inicial de una cuenta de personal que da de alta un {@code ADMIN_SISTEMA} (SPEC-03 RF-03.1,
 * FEAT-13-T1). Como {@link RolClienteInicialInterna}, no pasa por la gestión de roles: la cuenta
 * recién creada no tiene sesiones que revocar, y anunciarla con {@code usuario.roles_cambiados} antes
 * de {@code usuario.creado} induciría a los consumidores a crear registros de una cuenta que quizá
 * nunca se active. El permiso {@code usuario.crear} lo comprueba el alta antes de llamar. Nunca asigna
 * {@code CLIENTE}. Requiere la transacción del llamador.
 */
public interface RolInicialPersonalInterna {
    void asignar(UUID usuarioId, Rol rol);
}
