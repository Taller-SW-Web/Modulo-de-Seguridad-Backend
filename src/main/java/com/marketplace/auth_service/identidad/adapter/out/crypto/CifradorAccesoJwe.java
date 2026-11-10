package com.marketplace.auth_service.identidad.adapter.out.crypto;

import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.text.ParseException;
import java.util.Base64;
import java.util.Objects;

/**
 * Envoltura opcional del acceso de persona (FEAT-16): JWE anidado
 * {@code alg=dir}, {@code enc=A256GCM}, {@code cty=JWT} sobre el JWS RS256 de
 * siempre. Oculta los claims a quien mira el tráfico del navegador; la firma
 * interna sigue siendo la que autoriza.
 *
 * <p>La clave es simétrica (256 bits) y solo la conoce este servicio: un acceso
 * cifrado solo lo valida la API de Seguridad. Los módulos consumidores siguen
 * recibiendo el JWS, que validan con el JWKS (ADR-001).
 */
public final class CifradorAccesoJwe {

    private static final int BYTES_CLAVE = 32;
    private static final JWEHeader CABECERA = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM)
            .contentType("JWT")
            .build();

    private final SecretKey clave;

    private CifradorAccesoJwe(byte[] bytes) {
        this.clave = new SecretKeySpec(bytes, "AES");
    }

    /**
     * @param base64 la clave en Base64 estándar; debe decodificar a 32 bytes
     * @throws ClaveFirmaRsa.ClaveInvalidaException si no es Base64 o no mide 256 bits
     */
    public static CifradorAccesoJwe desdeBase64(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(Objects.requireNonNull(base64, "base64").trim());
        } catch (IllegalArgumentException e) {
            throw new ClaveFirmaRsa.ClaveInvalidaException("la clave de cifrado de accesos no es Base64");
        }
        if (bytes.length != BYTES_CLAVE) {
            throw new ClaveFirmaRsa.ClaveInvalidaException("la clave de cifrado de accesos debe medir 256 bits");
        }
        return new CifradorAccesoJwe(bytes);
    }

    /** Envuelve un JWS compacto en un JWE compacto de cinco partes. */
    public String cifrar(String jws) {
        JWEObject jwe = new JWEObject(CABECERA, new Payload(Objects.requireNonNull(jws, "jws")));
        try {
            jwe.encrypt(new DirectEncrypter(clave));
        } catch (JOSEException e) {
            throw new IllegalStateException("no se pudo cifrar el acceso", e);
        }
        return jwe.serialize();
    }

    /**
     * Devuelve el JWS interno de un JWE propio. Solo admite la cabecera que
     * emite {@link #cifrar}: otro algoritmo, otro contenido, otra clave o un
     * texto alterado son un token inválido.
     */
    public String descifrar(String token) {
        try {
            JWEObject jwe = JWEObject.parse(token);
            JWEHeader cabecera = jwe.getHeader();
            if (!JWEAlgorithm.DIR.equals(cabecera.getAlgorithm())
                    || !EncryptionMethod.A256GCM.equals(cabecera.getEncryptionMethod())
                    || !"JWT".equals(cabecera.getContentType())) {
                throw new TokenInvalidoException("cifrado no admitido");
            }
            jwe.decrypt(new DirectDecrypter(clave));
            return jwe.getPayload().toString();
        } catch (ParseException | JOSEException | IllegalStateException e) {
            throw new TokenInvalidoException("acceso cifrado ilegible");
        }
    }

    /** Un JWE compacto tiene cinco partes; un JWS, tres. */
    public static boolean esCifrado(String token) {
        return token != null && token.chars().filter(c -> c == '.').count() == 4;
    }
}
