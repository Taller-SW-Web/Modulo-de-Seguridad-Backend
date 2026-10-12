package com.marketplace.auth_service.identidad.adapter.out.crypto;

import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.TokensDePrueba;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ClaimsVerificados;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verificación criptográfica con tokens realmente firmados en el runtime del
 * backend (TEC-IDENTIDAD-T002/T004): solo pasa RS256 con nuestra clave y nuestro kid.
 */
class VerificacionJwsTest {

    private static final String ISSUER = "https://g7.example.test/api/v1/auth";
    private static final KeyPair NUESTRA = ClavesDePrueba.RSA_3072;
    private static final String KID = TokensDePrueba.kidDe(NUESTRA);

    private final NimbusFirmadorJws cripto = new NimbusFirmadorJws(ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(NUESTRA)));

    @Test
    void unAccesoFirmadoPorNosotrosSeVerificaYSeLeenSusClaims() {
        UUID usuario = UUID.randomUUID();
        var acceso = new FirmarAccesoService(cripto, ISSUER, Clock.systemUTC())
                .firmar(new ClaimsAcceso(usuario, "a@ejemplo.com", List.of("ADMIN_SISTEMA"), List.of("rol.asignar")));

        ClaimsVerificados claims = cripto.verificar(acceso.token());

        assertThat(claims.iss()).isEqualTo(ISSUER);
        assertThat(claims.tipo()).isEqualTo("acceso");
        assertThat(claims.sub()).isEqualTo(usuario.toString());
        assertThat(claims.jti()).isEqualTo(acceso.jti().toString());
        assertThat(claims.roles()).containsExactly("ADMIN_SISTEMA");
        assertThat(claims.permisos()).containsExactly("rol.asignar");
        assertThat(claims.exp()).isEqualTo(acceso.expiraEn());
    }

    @Test
    void laVigenciaNoSeDecideAquiSinoEnElCasoDeUso() {
        Instant hace1h = Instant.now().minusSeconds(3600);
        JWTClaimsSet vencido = TokensDePrueba.accesoValido(ISSUER)
                .issueTime(Date.from(hace1h.minusSeconds(900)))
                .expirationTime(Date.from(hace1h)).build();

        assertThat(cripto.verificar(TokensDePrueba.firmarRs256(vencido)).exp()).isBefore(Instant.now());
    }

    @Test
    void otraClaveNoPasaAunqueUseNuestroKid() {
        String falso = TokensDePrueba.firmarRsa(ClavesDePrueba.generar(3072), JWSAlgorithm.RS256, KID,
                TokensDePrueba.accesoValido(ISSUER).build());

        assertThatThrownBy(() -> cripto.verificar(falso)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void nuestraClaveConOtroKidOSinKidNoPasa() {
        JWTClaimsSet claims = TokensDePrueba.accesoValido(ISSUER).build();

        assertThatThrownBy(() -> cripto.verificar(TokensDePrueba.firmarRsa(NUESTRA, JWSAlgorithm.RS256, "otro", claims)))
                .isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> cripto.verificar(TokensDePrueba.firmarRsa(NUESTRA, JWSAlgorithm.RS256, null, claims)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void soloSeAdmiteRs256() {
        JWTClaimsSet claims = TokensDePrueba.accesoValido(ISSUER).build();

        assertThatThrownBy(() -> cripto.verificar(TokensDePrueba.firmarRsa(NUESTRA, JWSAlgorithm.RS512, KID, claims)))
                .as("RS512 con nuestra clave").isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> cripto.verificar(TokensDePrueba.firmarHs256(KID, claims)))
                .as("confusión de algoritmo HS256").isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> cripto.verificar(TokensDePrueba.sinFirma(claims)))
                .as("alg none").isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unTokenMalFormadoEsInvalido() {
        assertThatThrownBy(() -> cripto.verificar("no-es-un-jwt")).isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> cripto.verificar("a.b.c")).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void elMensajeDeErrorNoIncluyeElToken() {
        String falso = TokensDePrueba.firmarRsa(NUESTRA, JWSAlgorithm.RS256, "otro",
                TokensDePrueba.accesoValido(ISSUER).build());

        assertThatThrownBy(() -> cripto.verificar(falso)).message().doesNotContain(falso.substring(0, 20));
    }

    @Test
    void rolesQueNoSonArrayDeTextosLleganComoAusentes() {
        JWTClaimsSet claims = TokensDePrueba.accesoValido(ISSUER)
                .claim("roles", "ADMIN_SISTEMA")
                .claim("permisos", List.of(1, 2)).build();

        ClaimsVerificados verificados = cripto.verificar(TokensDePrueba.firmarRs256(claims));

        assertThat(verificados.roles()).isNull();
        assertThat(verificados.permisos()).isNull();
    }

    @Test
    void unTokenDeServicioConNuestraFirmaSeVerificaYSuPerfilLoDecideElCasoDeUso() {
        ClaimsVerificados claims = cripto.verificar(
                TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER).build()));

        assertThat(claims.tipo()).isEqualTo("servicio");
        assertThat(claims.sub()).isEqualTo("modulo-ventas");
    }

    @Test
    void elRelojDelCasoDeUsoRechazaElAccesoAlVencer() {
        UUID usuario = UUID.randomUUID();
        Instant emision = Instant.parse("2026-10-12T20:00:00Z");
        String token = new FirmarAccesoService(cripto, ISSUER, Clock.fixed(emision, ZoneOffset.UTC))
                .firmar(new ClaimsAcceso(usuario, "a@ejemplo.com", List.of(), List.of())).token();

        FirmarAccesoService alMinuto14 = new FirmarAccesoService(cripto, ISSUER,
                Clock.fixed(emision.plusSeconds(899), ZoneOffset.UTC));
        FirmarAccesoService alMinuto15 = new FirmarAccesoService(cripto, ISSUER,
                Clock.fixed(emision.plusSeconds(900), ZoneOffset.UTC));

        assertThat(alMinuto14.validar(token).sub()).isEqualTo(usuario);
        assertThatThrownBy(() -> alMinuto15.validar(token)).isInstanceOf(TokenInvalidoException.class);
    }
}
