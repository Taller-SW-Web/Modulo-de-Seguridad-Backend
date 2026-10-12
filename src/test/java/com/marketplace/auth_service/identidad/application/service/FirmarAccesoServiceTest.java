package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ClaimsVerificados;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;
import com.marketplace.auth_service.identidad.application.dto.Descubrimiento;
import com.marketplace.auth_service.identidad.application.port.out.FirmadorJws;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Núcleo de la firma sin Spring ni criptografía: tiempos, issuer y claims. */
class FirmarAccesoServiceTest {

    private static final String ISSUER = "https://g7.example.test/api/v1/auth";
    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final ClavePublica CLAVE = new ClavePublica("kid-1", "bW9kdWxv", "AQAB");

    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00.750Z");

    private final List<ContenidoAcceso> firmados = new ArrayList<>();
    /** Lo que devolverá el doble al verificar; cada prueba de validación lo ajusta. */
    private ClaimsVerificados verificados;
    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final FirmarAccesoService servicio = new FirmarAccesoService(new FirmadorFalso(), ISSUER, reloj);

    /** Doble del puerto de salida: registra lo que se pidió firmar. */
    private class FirmadorFalso implements FirmadorJws {
        @Override
        public String firmar(ContenidoAcceso contenido) {
            firmados.add(contenido);
            return "jws";
        }

        @Override
        public List<ClavePublica> clavesPublicas() {
            return List.of(CLAVE);
        }

        @Override
        public ClaimsVerificados verificar(String token) {
            if ("roto".equals(token)) {
                throw new TokenInvalidoException("firma inválida");
            }
            return verificados;
        }
    }

    /** Claims de un acceso correcto que vence dentro de un minuto. */
    private static ClaimsVerificados acceso(String tipo, Instant exp) {
        return new ClaimsVerificados(ISSUER, tipo, USUARIO.toString(), UUID.randomUUID().toString(),
                List.of("CLIENTE"), List.of(), exp);
    }

    @Test
    void firmaUnAccesoDe900SegundosConElRelojInyectado() {
        AccesoFirmado acceso = servicio.firmar(
                new ClaimsAcceso(USUARIO, "a@ejemplo.com", List.of("CLIENTE"), List.of()));

        ContenidoAcceso contenido = firmados.getFirst();
        assertThat(contenido.iat()).isEqualTo(Instant.parse("2026-10-12T20:00:00Z"));
        assertThat(contenido.exp()).isEqualTo(Instant.parse("2026-10-12T20:15:00Z"));
        assertThat(acceso.expiresIn()).isEqualTo(900);
        assertThat(acceso.emitidoEn()).isEqualTo(contenido.iat());
        assertThat(acceso.expiraEn()).isEqualTo(contenido.exp());
        assertThat(acceso.token()).isEqualTo("jws");
    }

    @Test
    void firmaLosClaimsRecibidosConElIssuerDelEntorno() {
        servicio.firmar(new ClaimsAcceso(USUARIO, "a@ejemplo.com",
                List.of("ADMIN_SISTEMA", "VENDEDOR"), List.of("usuario.ver", "rol.asignar")));

        ContenidoAcceso contenido = firmados.getFirst();
        assertThat(contenido.issuer()).isEqualTo(ISSUER);
        assertThat(contenido.sub()).isEqualTo(USUARIO);
        assertThat(contenido.email()).isEqualTo("a@ejemplo.com");
        assertThat(contenido.roles()).containsExactly("ADMIN_SISTEMA", "VENDEDOR");
        assertThat(contenido.permisos()).containsExactly("usuario.ver", "rol.asignar");
    }

    @Test
    void cadaEmisionLlevaUnJtiNuevo() {
        ClaimsAcceso claims = new ClaimsAcceso(USUARIO, "a@ejemplo.com", List.of(), List.of());

        AccesoFirmado primero = servicio.firmar(claims);
        AccesoFirmado segundo = servicio.firmar(claims);

        assertThat(primero.jti()).isNotEqualTo(segundo.jti());
        assertThat(firmados).extracting(ContenidoAcceso::jti).containsExactly(primero.jti(), segundo.jti());
    }

    @Test
    void rolesYPermisosVaciosSeConservanComoListas() {
        servicio.firmar(new ClaimsAcceso(USUARIO, "a@ejemplo.com", List.of(), List.of()));

        assertThat(firmados.getFirst().roles()).isNotNull().isEmpty();
        assertThat(firmados.getFirst().permisos()).isNotNull().isEmpty();
    }

    @Test
    void claimsIncompletosSeRechazanAntesDeFirmar() {
        assertThatThrownBy(() -> new ClaimsAcceso(null, "a@ejemplo.com", List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClaimsAcceso(USUARIO, " ", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClaimsAcceso(USUARIO, "a@ejemplo.com", null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThat(firmados).isEmpty();
    }

    @Test
    void laFirmaDenegadaNoEmiteNiPublicaNada() {
        FirmaDenegada denegada = new FirmaDenegada("no hay clave privada configurada");

        assertThatThrownBy(() -> denegada.firmar(new ClaimsAcceso(USUARIO, "a@ejemplo.com", List.of(), List.of())))
                .isInstanceOf(FirmaNoDisponibleException.class)
                .hasMessage("no hay clave privada configurada");
        assertThatThrownBy(denegada::jwks).isInstanceOf(FirmaNoDisponibleException.class);
        assertThatThrownBy(denegada::descubrimiento).isInstanceOf(FirmaNoDisponibleException.class);
    }

    @Test
    void elJwksPublicaLasClavesDelFirmador() {
        assertThat(servicio.jwks()).containsExactly(CLAVE);
    }

    @Test
    void elDescubrimientoDerivaElJwksDelIssuerConfigurado() {
        Descubrimiento descubrimiento = servicio.descubrimiento();

        assertThat(descubrimiento.issuer()).isEqualTo(ISSUER);
        assertThat(descubrimiento.jwksUri()).isEqualTo(ISSUER + "/.well-known/jwks.json");
        assertThat(descubrimiento.algoritmosFirma()).containsExactly("RS256");
    }

    @Test
    void elIssuerDelDescubrimientoEsElMismoQueElIssDeLosAccesos() {
        servicio.firmar(new ClaimsAcceso(USUARIO, "a@ejemplo.com", List.of(), List.of()));

        assertThat(servicio.descubrimiento().issuer()).isEqualTo(firmados.getFirst().issuer());
    }

    @Test
    void elToStringDelAccesoNoExponeElToken() {
        AccesoFirmado acceso = new AccesoFirmado("eyJ.secreto.firma", UUID.randomUUID(),
                Instant.EPOCH, Instant.EPOCH.plusSeconds(900));

        assertThat(acceso.toString()).doesNotContain("eyJ");
    }

    // ── validar (TEC-IDENTIDAD-T002) ──────────────────────────

    @Test
    void unAccesoVigenteSeValidaConSusClaims() {
        verificados = acceso("acceso", AHORA.plusSeconds(60));

        AccesoValidado validado = servicio.validar("jws");

        assertThat(validado.sub()).isEqualTo(USUARIO);
        assertThat(validado.roles()).containsExactly("CLIENTE");
        assertThat(validado.permisos()).isEmpty();
        assertThat(validado.expiraEn()).isEqualTo(AHORA.plusSeconds(60));
    }

    @Test
    void venceExactamenteEnExpSinTolerancia() {
        verificados = acceso("acceso", AHORA);
        assertThatThrownBy(() -> servicio.validar("jws")).isInstanceOf(TokenInvalidoException.class);

        verificados = acceso("acceso", AHORA.minusSeconds(1));
        assertThatThrownBy(() -> servicio.validar("jws")).isInstanceOf(TokenInvalidoException.class);

        verificados = acceso("acceso", AHORA.plusMillis(1));
        assertThat(servicio.validar("jws").sub()).isEqualTo(USUARIO);
    }

    @Test
    void unTokenDeServicioVigenteNoEsAplicableAOperacionesDePersona() {
        verificados = acceso("servicio", AHORA.plusSeconds(60));

        assertThatThrownBy(() -> servicio.validar("jws")).isInstanceOf(TokenNoAplicableException.class);
    }

    @Test
    void unTokenDeServicioVencidoEsSimplementeInvalido() {
        verificados = acceso("servicio", AHORA.minusSeconds(1));

        assertThatThrownBy(() -> servicio.validar("jws")).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void otrosPerfilesSonInvalidos() {
        for (String tipo : new String[]{"refresco", "ACCESO", "", null}) {
            verificados = acceso(tipo, AHORA.plusSeconds(60));
            assertThatThrownBy(() -> servicio.validar("jws")).as("tipo %s", tipo)
                    .isInstanceOf(TokenInvalidoException.class);
        }
    }

    @Test
    void elIssuerDebeSerExactamenteElDelEntorno() {
        for (String iss : new String[]{ISSUER + "/", "https://otro.example.test/api/v1/auth", null}) {
            verificados = new ClaimsVerificados(iss, "acceso", USUARIO.toString(), UUID.randomUUID().toString(),
                    List.of(), List.of(), AHORA.plusSeconds(60));
            assertThatThrownBy(() -> servicio.validar("jws")).as("iss %s", iss)
                    .isInstanceOf(TokenInvalidoException.class);
        }
    }

    @Test
    void subYJtiDebenSerUuid() {
        verificados = new ClaimsVerificados(ISSUER, "acceso", "modulo-ventas", UUID.randomUUID().toString(),
                List.of(), List.of(), AHORA.plusSeconds(60));
        assertThatThrownBy(() -> servicio.validar("jws")).isInstanceOf(TokenInvalidoException.class);

        verificados = new ClaimsVerificados(ISSUER, "acceso", USUARIO.toString(), null,
                List.of(), List.of(), AHORA.plusSeconds(60));
        assertThatThrownBy(() -> servicio.validar("jws")).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rolesYPermisosDebenSerArrays() {
        verificados = new ClaimsVerificados(ISSUER, "acceso", USUARIO.toString(), UUID.randomUUID().toString(),
                null, List.of(), AHORA.plusSeconds(60));

        assertThatThrownBy(() -> servicio.validar("jws")).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void sinTokenOConFirmaInvalidaEsInvalido() {
        assertThatThrownBy(() -> servicio.validar(null)).isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> servicio.validar(" ")).isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> servicio.validar("roto")).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void sinClaveTampocoSeValida() {
        assertThatThrownBy(() -> new FirmaDenegada("sin clave").validar("jws"))
                .isInstanceOf(FirmaNoDisponibleException.class);
    }
}
