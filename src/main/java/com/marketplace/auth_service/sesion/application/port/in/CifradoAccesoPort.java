package com.marketplace.auth_service.sesion.application.port.in;

import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;

/**
 * Formato opcional del acceso entregado (FEAT-16). Lo pide el cliente en
 * login, verificación de OTP y renovación; sin pedirlo, la sesión se entrega
 * con el JWS de siempre.
 */
public interface CifradoAccesoPort {

    /** Devuelve la misma sesión con el acceso envuelto en JWE. El refresco y el usuario no cambian. */
    SesionIniciada cifrar(SesionIniciada sesion);
}
