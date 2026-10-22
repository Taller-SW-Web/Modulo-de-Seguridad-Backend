package com.marketplace.auth_service.identidad.application.port.in;

import com.marketplace.auth_service.identidad.application.dto.ResultadoTokenServicio;
import com.marketplace.auth_service.identidad.application.dto.SolicitudTokenServicio;

/**
 * Emisión de tokens de servicio (SPEC-17 RF-17.4/17.5/17.12/17.13, ESC-17.3).
 * Autentica al módulo consumidor con su {@code client_id} y {@code client_secret}
 * y firma un token con los scopes que tiene concedidos.
 */
public interface TokenServicioPort {

    ResultadoTokenServicio emitir(SolicitudTokenServicio solicitud);
}
