package com.marketplace.auth_service.identidad.application.port.out;

import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;

/** Firma criptográfica de los claims: la implementa el adaptador de cripto. */
public interface FirmadorJws {

    /** Devuelve el JWS compacto, firmado RS256 con la clave persistente. */
    String firmar(ContenidoAcceso contenido);
}
