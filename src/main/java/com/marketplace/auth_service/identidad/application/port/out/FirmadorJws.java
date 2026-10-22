package com.marketplace.auth_service.identidad.application.port.out;

import com.marketplace.auth_service.identidad.application.dto.ClaimsVerificados;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;
import com.marketplace.auth_service.identidad.application.dto.ContenidoServicio;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;

import java.util.List;

/** Firma y verificación criptográfica con la clave del entorno: la implementa el adaptador de cripto. */
public interface FirmadorJws {

    /** Devuelve el JWS compacto, firmado RS256 con la clave persistente. */
    String firmar(ContenidoAcceso contenido);

    /** Igual que {@link #firmar}, con el perfil de servicio (SPEC-17 RF-17.4/17.5). */
    String firmarServicio(ContenidoServicio contenido);

    /**
     * Parte pública de las claves publicadas: la de firma y, durante una
     * rotación, la anterior (RF-17.3). Nunca parámetros privados.
     */
    List<ClavePublica> clavesPublicas();

    /**
     * Comprueba firma RS256 con una clave pública local publicada y que el
     * {@code kid} sea uno de los nuestros. No mira issuer, vigencia ni perfil:
     * eso lo decide el caso de uso.
     *
     * @throws TokenInvalidoException si el token está mal formado o la firma/kid no valen
     */
    ClaimsVerificados verificar(String token);
}
