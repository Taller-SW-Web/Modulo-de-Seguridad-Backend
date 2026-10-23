package com.marketplace.auth_service.identidad.adapter.out.crypto;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Par RSA de firma del entorno (ENTORNO-Y-REACT §2): privada PKCS8 externa,
 * pública derivada de ella y {@code kid} = huella SHA-256 de la JWK pública
 * (RFC 7638). La misma clave produce siempre el mismo kid, así que un reinicio
 * conserva la verificación de los accesos ya emitidos.
 *
 * <p>Solo se acepta PKCS8 sin cifrar ({@code BEGIN PRIVATE KEY}) de al menos
 * 3072 bits. Los mensajes de error describen el problema sin incluir la clave.
 */
public final class ClaveFirmaRsa {

    public static final int BITS_MINIMOS = 3072;

    private static final String INICIO_PKCS8 = "-----BEGIN PRIVATE KEY-----";
    private static final String FIN_PKCS8 = "-----END PRIVATE KEY-----";

    private final RSAKey jwk;

    private ClaveFirmaRsa(RSAKey jwk) {
        this.jwk = jwk;
    }

    /**
     * Carga la clave desde su texto PEM.
     *
     * @throws ClaveInvalidaException si no es una RSA PKCS8 válida de 3072 bits o más
     */
    public static ClaveFirmaRsa desdePem(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new ClaveInvalidaException("la clave privada está vacía");
        }
        String texto = pem.strip();
        if (!texto.startsWith(INICIO_PKCS8) || !texto.endsWith(FIN_PKCS8)) {
            throw new ClaveInvalidaException(
                    "la clave privada debe estar en PEM PKCS8 sin cifrar (BEGIN PRIVATE KEY)");
        }
        byte[] der;
        try {
            String base64 = texto.substring(INICIO_PKCS8.length(), texto.length() - FIN_PKCS8.length());
            der = Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new ClaveInvalidaException("el contenido PEM no es Base64 válido");
        }

        RSAPrivateCrtKey privada;
        RSAPublicKey publica;
        try {
            KeyFactory fabrica = KeyFactory.getInstance("RSA");
            if (!(fabrica.generatePrivate(new PKCS8EncodedKeySpec(der)) instanceof RSAPrivateCrtKey crt)) {
                throw new ClaveInvalidaException("la clave RSA no incluye sus parámetros CRT");
            }
            privada = crt;
            publica = (RSAPublicKey) fabrica.generatePublic(
                    new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new ClaveInvalidaException("la clave privada no es una clave RSA PKCS8");
        }

        int bits = privada.getModulus().bitLength();
        if (bits < BITS_MINIMOS) {
            throw new ClaveInvalidaException(
                    "la clave RSA tiene " + bits + " bits; se exigen al menos " + BITS_MINIMOS);
        }

        try {
            RSAKey sinKid = new RSAKey.Builder(publica).privateKey(privada).build();
            RSAKey jwk = new RSAKey.Builder(publica)
                    .privateKey(privada)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID(sinKid.computeThumbprint("SHA-256").toString())
                    .build();
            return new ClaveFirmaRsa(jwk);
        } catch (JOSEException e) {
            throw new ClaveInvalidaException("no se pudo calcular el kid de la clave");
        }
    }

    /**
     * Carga la parte <b>pública</b> de una clave retirada para seguir publicándola
     * y aceptándola durante una rotación (RF-17.3). PEM X.509
     * ({@code BEGIN PUBLIC KEY}) de al menos 3072 bits; su {@code kid} es la misma
     * huella RFC 7638 que tenía cuando firmaba.
     *
     * @throws ClaveInvalidaException si no es una pública RSA válida
     */
    public static RSAKey publicaDesdePem(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new ClaveInvalidaException("la clave pública anterior está vacía");
        }
        String texto = pem.strip();
        String inicio = "-----BEGIN PUBLIC KEY-----";
        String fin = "-----END PUBLIC KEY-----";
        if (!texto.startsWith(inicio) || !texto.endsWith(fin)) {
            throw new ClaveInvalidaException("la clave pública anterior debe estar en PEM X.509 (BEGIN PUBLIC KEY)");
        }
        RSAPublicKey publica;
        try {
            byte[] der = Base64.getMimeDecoder().decode(texto.substring(inicio.length(), texto.length() - fin.length()));
            if (!(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der)) instanceof RSAPublicKey rsa)) {
                throw new ClaveInvalidaException("la clave pública anterior no es RSA");
            }
            publica = rsa;
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new ClaveInvalidaException("la clave pública anterior no es una clave RSA X.509 válida");
        }
        int bits = publica.getModulus().bitLength();
        if (bits < BITS_MINIMOS) {
            throw new ClaveInvalidaException(
                    "la clave pública anterior tiene " + bits + " bits; se exigen al menos " + BITS_MINIMOS);
        }
        try {
            RSAKey sinKid = new RSAKey.Builder(publica).build();
            return new RSAKey.Builder(publica)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID(sinKid.computeThumbprint("SHA-256").toString())
                    .build();
        } catch (JOSEException e) {
            throw new ClaveInvalidaException("no se pudo calcular el kid de la clave pública anterior");
        }
    }

    public String kid() {
        return jwk.getKeyID();
    }

    /** JWK con la parte privada: solo para el firmador, nunca para publicar. */
    RSAKey jwkPrivada() {
        return jwk;
    }

    /** JWK solo pública (kty, use, alg, kid, n, e): la que publicará el JWKS. */
    public RSAKey jwkPublica() {
        return jwk.toPublicJWK();
    }

    @Override
    public String toString() {
        return "ClaveFirmaRsa[kid=" + kid() + "]";
    }

    /** La clave configurada no sirve para firmar. */
    public static class ClaveInvalidaException extends RuntimeException {
        public ClaveInvalidaException(String motivo) {
            super(motivo);
        }
    }
}
