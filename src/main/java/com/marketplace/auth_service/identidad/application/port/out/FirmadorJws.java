package com.marketplace.auth_service.identidad.application.port.out;

import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;

import java.util.List;

/** Firma criptográfica de los claims: la implementa el adaptador de cripto. */
public interface FirmadorJws {

    /** Devuelve el JWS compacto, firmado RS256 con la clave persistente. */
    String firmar(ContenidoAcceso contenido);

    /** Parte pública de las claves con las que se firma; nunca parámetros privados. */
    List<ClavePublica> clavesPublicas();
}
