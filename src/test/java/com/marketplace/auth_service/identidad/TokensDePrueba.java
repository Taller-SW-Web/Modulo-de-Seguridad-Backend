package com.marketplace.auth_service.identidad;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Fabrica tokens arbitrarios para probar el rechazo: kid ajeno, otros algoritmos,
 * perfiles de servicio o refresco, claims ausentes. Firma con las claves de
 * {@link ClavesDePrueba}, nunca con una clave real.
 */
public final class TokensDePrueba {

    private TokensDePrueba() {
    }

    /** kid que corresponde a un par (huella SHA-256 de su JWK pública), igual que ClaveFirmaRsa. */
    public static String kidDe(KeyPair par) {
        try {
            return new RSAKey.Builder((RSAPublicKey) par.getPublic()).build().computeThumbprint("SHA-256").toString();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Claims de un acceso de persona correcto, para modificarlos en cada prueba. */
    public static JWTClaimsSet.Builder accesoValido(String issuer) {
        Instant ahora = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .claim("email", "p@ejemplo.com")
                .claim("roles", List.of("CLIENTE"))
                .claim("permisos", List.of())
                .claim("tipo", "acceso")
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plusSeconds(900)))
                .jwtID(UUID.randomUUID().toString());
    }

    /** Claims de un token de servicio (SPEC-17): sub client_id, aud y scope; sin roles. */
    public static JWTClaimsSet.Builder servicioValido(String issuer) {
        Instant ahora = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("modulo-ventas")
                .audience("api-seguridad")
                .claim("tipo", "servicio")
                .claim("scope", "usuarios:leer")
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plusSeconds(3600)))
                .jwtID(UUID.randomUUID().toString());
    }

    /** Firma RS256 (o el algoritmo RSA indicado) con el par y el kid dados; kid null = sin kid. */
    public static String firmarRsa(KeyPair par, JWSAlgorithm algoritmo, String kid, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algoritmo).keyID(kid).build(), claims);
            jwt.sign(new RSASSASigner(par.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String firmarRs256(JWTClaimsSet claims) {
        return firmarRsa(ClavesDePrueba.RSA_3072, JWSAlgorithm.RS256, kidDe(ClavesDePrueba.RSA_3072), claims);
    }

    /** HS256 con un secreto: un atacante que intenta la confusión de algoritmos. */
    public static String firmarHs256(String kid, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(kid).build(), claims);
            jwt.sign(new MACSigner(new byte[32]));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Token sin firma ({@code alg: none}). */
    public static String sinFirma(JWTClaimsSet claims) {
        return new PlainJWT(claims).serialize();
    }
}
