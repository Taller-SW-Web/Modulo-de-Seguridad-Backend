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

    /**
     * Completa el login de una cuenta con MFA (SPEC-09 RF-09.10/09.11): verifica el código y,
     * en el mismo commit, revalida cuenta, protección y credencial, aplica la caducidad y abre
     * la familia. Los fallos de código confirman su intento antes del 401.
     */
    ResultadoLogin completarMfa(String challengeToken, String codigo, ContextoPeticion contexto);
}
