package com.marketplace.auth_service.identidad.adapter.out.crypto;

import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.InspeccionAcceso;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FEAT-16: el acceso cifrado opcional. Se emite el mismo JWS RS256 y, si se
 * pide, se envuelve en JWE {@code dir}/{@code A256GCM}; la API acepta ambos.
 */
class AccesoCifradoTest {

    private static final String ISSUER = "https://g7.example.test/api/v1/auth";
    private static final UUID USUARIO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CLAVE = base64(32, (byte) 7);
    private static final String OTRA_CLAVE = base64(32, (byte) 9);

    private final ClaveFirmaRsa clave = ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));

    private FirmarAccesoService servicio(String claveCifrado) {
        CifradorAccesoJwe cifrador = claveCifrado == null ? null : CifradorAccesoJwe.desdeBase64(claveCifrado);
        return new FirmarAccesoService(new NimbusFirmadorJws(clave, List.of(), cifrador), ISSUER, Clock.systemUTC());
    }

    private static AccesoFirmado firmar(FirmarAccesoService servicio) {
        return servicio.firmar(new ClaimsAcceso(USUARIO, "b@ejemplo.com", List.of("ADMIN_SISTEMA"),
                List.of("usuario.ver", "rol.asignar")));
    }

    private static String base64(int bytes, byte valor) {
        byte[] datos = new byte[bytes];
        java.util.Arrays.fill(datos, valor);
        return Base64.getEncoder().encodeToString(datos);
    }

    @Test
    void laEmisionPorDefectoSigueSiendoElJwsDeSiempre() {
        String token = firmar(servicio(CLAVE)).token();

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(CifradorAccesoJwe.esCifrado(token)).isFalse();
    }

    @Test
    void elAccesoCifradoEsUnJweQueNoDejaLeerSusClaims() throws Exception {
        FirmarAccesoService servicio = servicio(CLAVE);
        String cifrado = servicio.cifrarAcceso(firmar(servicio).token());

        assertThat(cifrado.split("\\.", -1)).hasSize(5);
        JWEObject jwe = JWEObject.parse(cifrado);
        assertThat(jwe.getHeader().getAlgorithm()).isEqualTo(JWEAlgorithm.DIR);
        assertThat(jwe.getHeader().getEncryptionMethod()).isEqualTo(EncryptionMethod.A256GCM);
        assertThat(jwe.getHeader().getContentType()).isEqualTo("JWT");
        for (String parte : cifrado.split("\\.", -1)) {
            String visible = new String(Base64.getUrlDecoder().decode(parte), StandardCharsets.ISO_8859_1);
            assertThat(visible).doesNotContain("ADMIN_SISTEMA", "usuario.ver", "b@ejemplo.com", "roles");
        }
    }

    @Test
    void ambosFormatosAutorizanIgual() {
        FirmarAccesoService servicio = servicio(CLAVE);
        AccesoFirmado firmado = firmar(servicio);

        AccesoValidado plano = servicio.validar(firmado.token());
        AccesoValidado cifrado = servicio.validar(servicio.cifrarAcceso(firmado.token()));

        assertThat(cifrado).isEqualTo(plano);
        assertThat(cifrado.roles()).containsExactly("ADMIN_SISTEMA");
        assertThat(cifrado.permisos()).containsExactly("usuario.ver", "rol.asignar");
    }

    @Test
    void laIntrospeccionAceptaElAccesoCifrado() {
        FirmarAccesoService servicio = servicio(CLAVE);

        InspeccionAcceso inspeccion = servicio.inspeccionar(servicio.cifrarAcceso(firmar(servicio).token()));

        assertThat(inspeccion).isInstanceOf(InspeccionAcceso.Valido.class);
    }

    @Test
    void unAccesoCifradoAlteradoEsInvalido() {
        FirmarAccesoService servicio = servicio(CLAVE);
        String cifrado = servicio.cifrarAcceso(firmar(servicio).token());
        String[] partes = cifrado.split("\\.", -1);
        // El primer carácter siempre lleva bits de datos; el último puede ser solo relleno.
        char primero = partes[3].charAt(0);
        partes[3] = (primero == 'A' ? 'B' : 'A') + partes[3].substring(1);

        assertThatThrownBy(() -> servicio.validar(String.join(".", partes)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unAccesoCifradoConOtraClaveEsInvalido() {
        FirmarAccesoService ajeno = servicio(OTRA_CLAVE);
        String cifradoAjeno = ajeno.cifrarAcceso(firmar(ajeno).token());

        assertThatThrownBy(() -> servicio(CLAVE).validar(cifradoAjeno))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void sinClaveDeCifradoSeEntregaElJwsYUnJweSeRechaza() {
        FirmarAccesoService sinClave = servicio(null);
        String token = firmar(sinClave).token();

        assertThat(sinClave.cifrarAcceso(token)).isEqualTo(token);

        FirmarAccesoService conClave = servicio(CLAVE);
        String cifrado = conClave.cifrarAcceso(firmar(conClave).token());
        assertThatThrownBy(() -> sinClave.validar(cifrado)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void laClaveDebeSerBase64DeDoscientosCincuentaYSeisBits() {
        assertThatThrownBy(() -> CifradorAccesoJwe.desdeBase64(base64(16, (byte) 1)))
                .isInstanceOf(ClaveFirmaRsa.ClaveInvalidaException.class);
        assertThatThrownBy(() -> CifradorAccesoJwe.desdeBase64("esto no es base64!"))
                .isInstanceOf(ClaveFirmaRsa.ClaveInvalidaException.class);
    }
}
