package com.marketplace.auth_service.identidad.adapter.out.crypto;

import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.TokensDePrueba;
import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ClaimsServicio;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.InspeccionAcceso;
import com.marketplace.auth_service.identidad.application.dto.ServicioValidado;
import com.marketplace.auth_service.identidad.application.dto.TokenServicioFirmado;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tokens de servicio, inspección para la introspección y rotación de clave
 * (SPEC-17 RF-17.3/17.5/17.12/17.13, ESC-17.2/17.3) con la firma real RS256.
 */
class TokenServicioCriptoTest {

    private static final String ISSUER = "https://g7.example.test/api/v1/auth";
    private static final KeyPair ANTERIOR = ClavesDePrueba.generar(3072);

    private final ClaveFirmaRsa clave = ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    private final FirmarAccesoService servicio =
            new FirmarAccesoService(new NimbusFirmadorJws(clave), ISSUER, Clock.systemUTC());

    private TokenServicioFirmado servicio(List<String> audiencias, List<String> scopes) {
        return servicio.firmarServicio(new ClaimsServicio("modulo-despacho", audiencias, scopes));
    }

    // ── Emisión ──────────────────────────────────────────────

    @Test
    void elTokenDeServicioLlevaElPerfilDelContrato() throws Exception {
        TokenServicioFirmado token = servicio(List.of("api-despacho", "api-seguridad"),
                List.of("cotizaciones:calcular", "usuarios:leer"));

        var claims = SignedJWT.parse(token.token()).getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getSubject()).isEqualTo("modulo-despacho");
        assertThat(claims.getAudience()).containsExactly("api-despacho", "api-seguridad");
        assertThat(claims.getStringClaim("tipo")).isEqualTo("servicio");
        assertThat(claims.getClaim("scope")).isEqualTo("cotizaciones:calcular usuarios:leer");
        assertThat(claims.getClaims()).doesNotContainKeys("roles", "permisos", "email");
        assertThat(claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()).isEqualTo(3_600_000);
        assertThat(claims.getJWTID()).isEqualTo(token.jti().toString());
        assertThat(token.expiresIn()).isEqualTo(3600);
        assertThat(token.toString()).doesNotContain(token.token());
        assertThat(JWSObject.parse(token.token()).getHeader().getKeyID()).isEqualTo(clave.kid());
    }

    // ── Validación del perfil de servicio ────────────────────

    @Test
    void unTokenDeServicioParaEstaApiSeValidaConSusScopes() {
        TokenServicioFirmado token = servicio(List.of("api-seguridad"), List.of("roles:leer", "usuarios:leer"));

        ServicioValidado validado = servicio.validarServicio(token.token());

        assertThat(validado.clientId()).isEqualTo("modulo-despacho");
        assertThat(validado.jti()).isEqualTo(token.jti());
        assertThat(validado.scopes()).containsExactlyInAnyOrder("roles:leer", "usuarios:leer");
    }

    @Test
    void unTokenDeServicioDeOtraApiNoSirveAqui() {
        TokenServicioFirmado soloDespacho = servicio(List.of("api-despacho"), List.of("cotizaciones:calcular"));

        assertThatThrownBy(() -> servicio.validarServicio(soloDespacho.token()))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void personaYServicioNoSeConfunden() {
        AccesoFirmado acceso = servicio.firmar(new ClaimsAcceso(UUID.randomUUID(), "p@ejemplo.com",
                List.of("CLIENTE"), List.of()));
        TokenServicioFirmado deServicio = servicio(List.of("api-seguridad"), List.of("usuarios:leer"));

        assertThatThrownBy(() -> servicio.validarServicio(acceso.token())).isInstanceOf(TokenNoAplicableException.class);
        assertThatThrownBy(() -> servicio.validar(deServicio.token()))
                .isInstanceOfSatisfying(TokenNoAplicableException.class, e -> {
                    // ESC-17.4: identifica al módulo para auditar, sin llevar el token.
                    assertThat(e.clientId()).isEqualTo("modulo-despacho");
                    assertThat(e.jti()).isEqualTo(deServicio.jti().toString());
                    assertThat(e.getMessage()).doesNotContain(deServicio.token());
                });
    }

    @Test
    void unServicioConDuracionDistintaOVencidoEsInvalido() {
        Instant ahora = Instant.now();
        String largo = TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER)
                .audience("api-seguridad")
                .expirationTime(Date.from(ahora.plusSeconds(7200))).build());
        String vencido = TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER)
                .issueTime(Date.from(ahora.minusSeconds(3601))).expirationTime(Date.from(ahora.minusSeconds(1)))
                .build());

        assertThatThrownBy(() -> servicio.validarServicio(largo)).isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> servicio.validarServicio(vencido)).isInstanceOf(TokenInvalidoException.class);
    }

    // ── Inspección para la introspección ─────────────────────

    @Test
    void laInspeccionDistingueValidoVencidoEInvalido() {
        UUID usuario = UUID.randomUUID();
        AccesoFirmado acceso = servicio.firmar(new ClaimsAcceso(usuario, "p@ejemplo.com", List.of("VENDEDOR"), List.of()));
        Instant haceUnSegundo = Instant.now().minusSeconds(1);
        String vencido = TokensDePrueba.firmarRs256(TokensDePrueba.accesoValido(ISSUER)
                .issueTime(Date.from(haceUnSegundo.minusSeconds(900))).expirationTime(Date.from(haceUnSegundo)).build());
        String ajeno = TokensDePrueba.firmarRsa(ANTERIOR, JWSAlgorithm.RS256, TokensDePrueba.kidDe(ANTERIOR),
                TokensDePrueba.accesoValido(ISSUER).build());
        String deServicio = servicio(List.of("api-seguridad"), List.of("usuarios:leer")).token();

        assertThat(servicio.inspeccionar(acceso.token())).isInstanceOfSatisfying(InspeccionAcceso.Valido.class,
                v -> assertThat(v.acceso().sub()).isEqualTo(usuario));
        assertThat(servicio.inspeccionar(vencido)).isInstanceOf(InspeccionAcceso.Vencido.class);
        assertThat(servicio.inspeccionar(ajeno)).isInstanceOf(InspeccionAcceso.Invalido.class);
        assertThat(servicio.inspeccionar("no-es-un-jwt")).isInstanceOf(InspeccionAcceso.Invalido.class);
        assertThat(servicio.inspeccionar(deServicio)).isInstanceOf(InspeccionAcceso.Invalido.class);
    }

    // ── Rotación (RF-17.3, ESC-17.2) ─────────────────────────

    @Test
    void duranteLaRotacionSePublicanYAceptanLasDosClaves() {
        String pemAnterior = ClavesDePrueba.pem("PUBLIC KEY", ANTERIOR.getPublic().getEncoded());
        FirmarAccesoService rotado = new FirmarAccesoService(
                new NimbusFirmadorJws(clave, List.of(ClaveFirmaRsa.publicaDesdePem(pemAnterior))), ISSUER,
                Clock.systemUTC());
        String emitidoConLaAnterior = TokensDePrueba.firmarRsa(ANTERIOR, JWSAlgorithm.RS256,
                TokensDePrueba.kidDe(ANTERIOR), TokensDePrueba.accesoValido(ISSUER).build());

        assertThat(rotado.jwks()).extracting(ClavePublica::kid)
                .containsExactly(clave.kid(), TokensDePrueba.kidDe(ANTERIOR));
        assertThat(rotado.validar(emitidoConLaAnterior).roles()).containsExactly("CLIENTE");
        assertThat(rotado.firmar(new ClaimsAcceso(UUID.randomUUID(), "p@ejemplo.com", List.of(), List.of())).token())
                .satisfies(t -> assertThat(JWSObject.parse(t).getHeader().getKeyID()).isEqualTo(clave.kid()));
        assertThatThrownBy(() -> servicio.validar(emitidoConLaAnterior))
                .as("sin la clave anterior configurada, su kid es desconocido")
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void laClaveAnteriorDebeSerPublicaYDeTamanoSuficiente() {
        String privada = ClavesDePrueba.pemPkcs8(ANTERIOR);
        String corta = ClavesDePrueba.pem("PUBLIC KEY", ClavesDePrueba.generar(2048).getPublic().getEncoded());

        assertThatThrownBy(() -> ClaveFirmaRsa.publicaDesdePem(privada))
                .isInstanceOf(ClaveFirmaRsa.ClaveInvalidaException.class);
        assertThatThrownBy(() -> ClaveFirmaRsa.publicaDesdePem(corta))
                .isInstanceOf(ClaveFirmaRsa.ClaveInvalidaException.class);
        assertThat(ClaveFirmaRsa.publicaDesdePem(ClavesDePrueba.pem("PUBLIC KEY", ANTERIOR.getPublic().getEncoded()))
                .isPrivate()).isFalse();
    }

    @Test
    void elReinicioConLaMismaClaveConservaElKid() {
        assertThat(ClaveFirmaRsa.publicaDesdePem(ClavesDePrueba.pem("PUBLIC KEY",
                ClavesDePrueba.RSA_3072.getPublic().getEncoded())).getKeyID()).isEqualTo(clave.kid());
        assertThat(new NimbusFirmadorJws(clave, List.of(clave.jwkPublica())).clavesPublicas())
                .as("la anterior igual a la actual no se duplica").hasSize(1);
    }
}
