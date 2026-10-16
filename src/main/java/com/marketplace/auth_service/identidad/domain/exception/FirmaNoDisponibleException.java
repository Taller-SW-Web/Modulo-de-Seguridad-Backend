package com.marketplace.auth_service.identidad.domain.exception;

/**
 * No se puede emitir un acceso: falta la clave de firma o el issuer, o la
 * configuración es inválida (TEC-IDENTIDAD-T001). Se deniega la emisión en vez
 * de firmar con una clave efímera. El mensaje nunca incluye material de clave.
 */
public class FirmaNoDisponibleException extends RuntimeException {

    public FirmaNoDisponibleException(String motivo) {
        super(motivo);
    }
}
