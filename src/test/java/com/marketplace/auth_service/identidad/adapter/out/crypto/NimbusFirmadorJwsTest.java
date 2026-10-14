package com.marketplace.auth_service.identidad.adapter.out.crypto;

import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Firma real RS256 en el runtime del backend, verificada con la clave pública.
 * No reutiliza JWT ilustrativos del kit.
 */
class NimbusFirmadorJwsTest {

    private static final String ISSUER = "https://g7.example.test/api/v1/auth";
    private static final UUID USUARIO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final ClaveFirmaRsa clave = ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    private final NimbusJwtDecoder verificador =
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) ClavesDePrueba.RSA_3072.getPublic()).build();

    private FirmarAccesoService servicio(Clock reloj) {
        return new FirmarAccesoService(new NimbusFirmadorJws(clave), ISSUER, reloj);
    }

    private AccesoFirmado firmar(List<String> roles, List<String> permisos) {
        return servicio(Clock.systemUTC()).firmar(new ClaimsAcceso(USUARIO, "b@ejemplo.com", roles, permisos));
    }

    @Test
    void laFirmaSeVerificaConLaClavePublica() {
        AccesoFirmado acceso = firmar(List.of("CLIENTE"), List.of());

        Jwt jwt = verificador.decode(acceso.token());

        assertThat(jwt.getSubject()).isEqualTo(USUARIO.toString());
        assertThat(jwt.getId()).isEqualTo(acceso.jti().toString());
    }

    @Test
    void laCabeceraEsRs256ConElKidDeLaClave() throws Exception {
        JWSObject jws = JWSObject.parse(firmar(List.of(), List.of()).token());

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jws.getHeader().getKeyID()).isEqualTo(clave.kid());
        assertThat(jws.getHeader().getType().getType()).isEqualTo("JWT");
    }

    @Test
    void losClaimsSonExactamenteLosDelContrato() throws Exception {
        AccesoFirmado acceso = firmar(List.of("ADMIN_SISTEMA", "VENDEDOR"), List.of("usuario.ver", "rol.asignar"));

        Map<String, Object> claims = JWSObject.parse(acceso.token()).getPayload().toJSONObject();

        assertThat(claims).containsOnlyKeys("iss", "sub", "email", "roles", "permisos", "tipo", "iat", "exp", "jti");
        assertThat(claims).doesNotContainKey("aud");
        assertThat(claims.get("iss")).isEqualTo(ISSUER);
        assertThat(claims.get("tipo")).isEqualTo("acceso");
        assertThat(claims.get("email")).isEqualTo("b@ejemplo.com");
        assertThat(claims.get("roles")).isEqualTo(List.of("ADMIN_SISTEMA", "VENDEDOR"));
        assertThat(claims.get("permisos")).isEqualTo(List.of("usuario.ver", "rol.asignar"));
        assertThat(((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue()).isEqualTo(900);
    }

    @Test
    void rolesYPermisosVaciosViajanComoArraysVacios() throws Exception {
        Map<String, Object> claims = JWSObject.parse(firmar(List.of(), List.of()).token()).getPayload().toJSONObject();

        assertThat(claims.get("roles")).isEqualTo(List.of());
        assertThat(claims.get("permisos")).isEqualTo(List.of());
    }

    @Test
    void unaFirmaAlteradaNoSeVerifica() {
        String token = firmar(List.of("CLIENTE"), List.of()).token();
        String[] partes = token.split("\\.");
        char ultimo = partes[2].charAt(partes[2].length() - 2);
        String alterado = partes[0] + "." + partes[1] + "."
                + partes[2].substring(0, partes[2].length() - 2) + (ultimo == 'A' ? 'B' : 'A')
                + partes[2].charAt(partes[2].length() - 1);

        assertThatThrownBy(() -> verificador.decode(alterado)).isInstanceOf(JwtException.class);
    }

    @Test
    void otraClaveNoVerificaElToken() {
        NimbusJwtDecoder otro = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) ClavesDePrueba.generar(3072).getPublic()).build();

        assertThatThrownBy(() -> otro.decode(firmar(List.of(), List.of()).token())).isInstanceOf(JwtException.class);
    }

    @Test
    void unAccesoVencidoNoSeAcepta() {
        Clock haceUnaHora = Clock.fixed(Instant.now().minusSeconds(3600), ZoneOffset.UTC);
        String vencido = servicio(haceUnaHora).firmar(
                new ClaimsAcceso(USUARIO, "b@ejemplo.com", List.of(), List.of())).token();

        assertThatThrownBy(() -> verificador.decode(vencido)).isInstanceOf(JwtException.class);
    }

    @Test
    void unAccesoConDiezPermisosPesaMenosDe4Kb() {
        List<String> diez = List.of("usuario.ver", "usuario.crear", "usuario.desactivar", "usuario.reactivar",
                "cuenta.bloquear", "rol.asignar", "atributos.editar", "auditoria.ver", "cliente.registrar",
                "cliente.buscar");

        String token = firmar(List.of("ADMIN_SISTEMA", "VENDEDOR"), diez).token();

        assertThat(token.getBytes(StandardCharsets.US_ASCII).length).isLessThan(4096);
    }
}
