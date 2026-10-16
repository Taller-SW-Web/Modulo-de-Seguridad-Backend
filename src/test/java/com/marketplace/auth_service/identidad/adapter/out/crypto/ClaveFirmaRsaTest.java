package com.marketplace.auth_service.identidad.adapter.out.crypto;

import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.adapter.out.crypto.ClaveFirmaRsa.ClaveInvalidaException;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Carga de la clave PKCS8 del entorno, kid estable y rechazo de configuraciones inválidas. */
class ClaveFirmaRsaTest {

    private static final String PEM = ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072);

    @Test
    void elKidEsLaHuellaSha256DeLaJwkPublica() throws Exception {
        ClaveFirmaRsa clave = ClaveFirmaRsa.desdePem(PEM);

        String huella = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) ClavesDePrueba.RSA_3072.getPublic())
                .build().computeThumbprint("SHA-256").toString();
        assertThat(clave.kid()).isEqualTo(huella);
    }

    @Test
    void cargarLaMismaClaveDosVecesConservaElKid() {
        // Equivale a un reinicio: misma clave persistente, mismo kid.
        assertThat(ClaveFirmaRsa.desdePem(PEM).kid()).isEqualTo(ClaveFirmaRsa.desdePem(PEM).kid());
    }

    @Test
    void laJwkPublicaSoloLlevaParametrosPublicos() {
        RSAKey publica = ClaveFirmaRsa.desdePem(PEM).jwkPublica();

        assertThat(publica.isPrivate()).isFalse();
        assertThat(publica.toJSONObject()).containsOnlyKeys("kty", "use", "alg", "kid", "n", "e");
        assertThat(publica.toJSONObject()).containsEntry("use", "sig").containsEntry("alg", "RS256");
    }

    @Test
    void aceptaPemConEspaciosYSaltosDeLineaWindows() {
        String crlf = PEM.replace("\n", "\r\n");

        assertThat(ClaveFirmaRsa.desdePem("  " + crlf + "  ").kid()).isEqualTo(ClaveFirmaRsa.desdePem(PEM).kid());
    }

    @Test
    void rechazaUnaClaveDe2048Bits() {
        String pem2048 = ClavesDePrueba.pemPkcs8(ClavesDePrueba.generar(2048));

        assertThatThrownBy(() -> ClaveFirmaRsa.desdePem(pem2048))
                .isInstanceOf(ClaveInvalidaException.class)
                .hasMessageContaining("2048 bits");
    }

    @Test
    void rechazaFormatoPkcs1() {
        String pkcs1 = PEM.replace("PRIVATE KEY", "RSA PRIVATE KEY");

        assertThatThrownBy(() -> ClaveFirmaRsa.desdePem(pkcs1))
                .isInstanceOf(ClaveInvalidaException.class)
                .hasMessageContaining("PKCS8");
    }

    @Test
    void rechazaUnaClaveQueNoEsRsa() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("EC");
        generador.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ec = generador.generateKeyPair();

        assertThatThrownBy(() -> ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ec)))
                .isInstanceOf(ClaveInvalidaException.class);
    }

    @Test
    void rechazaContenidoVacioOCorrupto() {
        assertThatThrownBy(() -> ClaveFirmaRsa.desdePem("")).isInstanceOf(ClaveInvalidaException.class);
        assertThatThrownBy(() -> ClaveFirmaRsa.desdePem(null)).isInstanceOf(ClaveInvalidaException.class);
        assertThatThrownBy(() -> ClaveFirmaRsa.desdePem(
                "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----"))
                .isInstanceOf(ClaveInvalidaException.class);
    }

    @Test
    void losErroresYElToStringNoIncluyenMaterialDeClave() {
        String cuerpo = PEM.lines().skip(1).findFirst().orElseThrow();
        String pkcs1 = PEM.replace("PRIVATE KEY", "RSA PRIVATE KEY");

        assertThatThrownBy(() -> ClaveFirmaRsa.desdePem(pkcs1)).message().doesNotContain(cuerpo);
        assertThat(ClaveFirmaRsa.desdePem(PEM).toString()).doesNotContain(cuerpo).startsWith("ClaveFirmaRsa[kid=");
    }
}
