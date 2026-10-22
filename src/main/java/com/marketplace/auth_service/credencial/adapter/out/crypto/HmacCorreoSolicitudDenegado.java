package com.marketplace.auth_service.credencial.adapter.out.crypto;

import com.marketplace.auth_service.credencial.application.port.out.HashCorreoSolicitudPort;
import com.marketplace.auth_service.credencial.domain.exception.SolicitudRecuperacionNoDisponibleException;

/** Falla cerrada si no está configurada la clave persistente para el límite. */
public final class HmacCorreoSolicitudDenegado implements HashCorreoSolicitudPort {
    @Override public String generar(String correoNormalizado) {
        throw new SolicitudRecuperacionNoDisponibleException();
    }
    @Override public String toString() { return "HmacCorreoSolicitudDenegado[]"; }
}
