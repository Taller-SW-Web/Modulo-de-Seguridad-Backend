package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;
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

    private final List<ContenidoAcceso> firmados = new ArrayList<>();
    private final Clock reloj = Clock.fixed(Instant.parse("2026-10-12T20:00:00.750Z"), ZoneOffset.UTC);
    private final FirmarAccesoService servicio =
            new FirmarAccesoService(contenido -> { firmados.add(contenido); return "jws"; }, ISSUER, reloj);

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
    void laFirmaDenegadaNoEmiteNada() {
        FirmaDenegada denegada = new FirmaDenegada("no hay clave privada configurada");

        assertThatThrownBy(() -> denegada.firmar(new ClaimsAcceso(USUARIO, "a@ejemplo.com", List.of(), List.of())))
                .isInstanceOf(FirmaNoDisponibleException.class)
                .hasMessage("no hay clave privada configurada");
    }

    @Test
    void elToStringDelAccesoNoExponeElToken() {
        AccesoFirmado acceso = new AccesoFirmado("eyJ.secreto.firma", UUID.randomUUID(),
                Instant.EPOCH, Instant.EPOCH.plusSeconds(900));

        assertThat(acceso.toString()).doesNotContain("eyJ");
    }
}
