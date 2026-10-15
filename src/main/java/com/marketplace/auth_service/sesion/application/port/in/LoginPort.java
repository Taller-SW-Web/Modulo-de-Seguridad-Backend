package com.marketplace.auth_service.sesion.application.port.in;

import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

/**
 * Login con correo y contraseña (SPEC-05, EP-02-T006). Compone cuentas, credenciales,
 * protección, roles, MFA y sesiones por sus puertos. Un rechazo confirma su transacción
 * (el fallo contado queda registrado antes del 401).
 */
public interface LoginPort {
    ResultadoLogin iniciar(String correo, String contrasena, ContextoPeticion contexto);
}
