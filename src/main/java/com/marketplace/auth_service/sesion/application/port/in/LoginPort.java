package com.marketplace.auth_service.sesion.application.port.in;

import com.marketplace.auth_service.sesion.application.dto.CredencialesLogin;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

/**
 * Coordinador de login (EP-02-T006, ACUERDOS-INTERNOS §4). Compone cuentas,
 * credenciales, protección, MFA, roles y sesiones; no duplica sus reglas.
 */
public interface LoginPort {
    ResultadoLogin ingresar(CredencialesLogin credenciales, ContextoPeticion contexto);
}
