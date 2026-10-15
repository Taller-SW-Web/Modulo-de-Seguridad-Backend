package com.marketplace.auth_service.sesion.application.port.out;

import com.marketplace.auth_service.sesion.application.dto.RefrescoGenerado;

/**
 * Refrescos opacos propios: 32 bytes aleatorios en base64url sin padding y su
 * SHA-256 hexadecimal. El secreto vive una petición; la criptografía real está
 * en {@code adapter.out.crypto}.
 */
public interface CriptografiaSesionPort {
    RefrescoGenerado generarRefresco();
}
