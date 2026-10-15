package com.marketplace.auth_service.identidad.application.port.out;

import com.marketplace.auth_service.identidad.application.dto.ClaimsVerificados;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;

import java.util.List;

/** Firma y verificación criptográfica con la clave del entorno: la implementa el adaptador de cripto. */
public interface FirmadorJws {

    /** Devuelve el JWS compacto, firmado RS256 con la clave persistente. */
    String firmar(ContenidoAcceso contenido);

    /** Parte pública de las claves con las que se firma; nunca parámetros privados. */
    List<ClavePublica> clavesPublicas();

    /**
     * Comprueba firma RS256 con la clave pública local y que el {@code kid} sea
     * el nuestro. No mira issuer, vigencia ni perfil: eso lo decide el caso de uso.
     *
     * @throws TokenInvalidoException si el token está mal formado o la firma/kid no valen
     */
    ClaimsVerificados verificar(String token);
}
