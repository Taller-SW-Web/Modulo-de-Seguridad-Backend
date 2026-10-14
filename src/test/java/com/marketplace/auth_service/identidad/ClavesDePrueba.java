package com.marketplace.auth_service.identidad;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Pares RSA generados en la prueba: nunca claves reales del entorno. */
public final class ClavesDePrueba {

    /** Par de 3072 bits, generado una vez por ejecución (es lento). */
    public static final KeyPair RSA_3072 = generar(3072);

    private ClavesDePrueba() {
    }

    public static KeyPair generar(int bits) {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(bits);
            return generador.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** PEM PKCS8 ({@code BEGIN PRIVATE KEY}), como lo produce {@code openssl genpkey}. */
    public static String pemPkcs8(KeyPair par) {
        return pem("PRIVATE KEY", par.getPrivate().getEncoded());
    }

    public static String pem(String tipo, byte[] der) {
        String cuerpo = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + tipo + "-----\n" + cuerpo + "\n-----END " + tipo + "-----\n";
    }
}
