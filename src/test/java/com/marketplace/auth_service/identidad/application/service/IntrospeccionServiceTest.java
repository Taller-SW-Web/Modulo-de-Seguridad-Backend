package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.TokensDePrueba;
import com.marketplace.auth_service.identidad.adapter.out.crypto.ClaveFirmaRsa;
import com.marketplace.auth_service.identidad.adapter.out.crypto.NimbusFirmadorJws;
import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ClaimsServicio;
import com.marketplace.auth_service.identidad.application.dto.ResultadoIntrospeccion;
import com.marketplace.auth_service.identidad.application.dto.ResultadoIntrospeccion.Motivo;
import com.marketplace.auth_service.identidad.application.port.out.EstadoIdentidadPort;
import com.marketplace.auth_service.shared.domain.ActorServicio;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Introspección (SPEC-17 RF-17.6/17.7, ESC-17.5/17.6): el estado <b>de ahora</b>
 * con firma real y un doble del estado de sesiones, cuentas y roles.
 */
class IntrospeccionServiceTest {

    private static final String ISSUER = "https://g7.example.test/api/v1/auth";
    private static final ActorServicio PRODUCTOS =
            new ActorServicio("modulo-productos", UUID.randomUUID(), Set.of("tokens:introspeccion"));

    private final FirmarAccesoService firma = new FirmarAccesoService(
            new NimbusFirmadorJws(ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072))),
            ISSUER, Clock.systemUTC());

    private final Set<UUID> accesosVigentes = new HashSet<>();
    private final Map<UUID, EstadoIdentidadPort.Cuenta> cuentas = new HashMap<>();
    private final Map<UUID, List<String>> roles = new HashMap<>();

    private final IntrospeccionService introspeccion = new IntrospeccionService(firma, new EstadoIdentidadPort() {
        @Override
        public boolean accesoVigente(UUID sub, UUID jti) {
            return accesosVigentes.contains(jti);
        }

        @Override
        public Optional<Cuenta> cuenta(UUID sub) {
            return Optional.ofNullable(cuentas.get(sub));
        }

        @Override
        public Permisos permisos(UUID sub) {
            List<String> actuales = roles.getOrDefault(sub, List.of());
            return new Permisos(actuales, actuales.contains("ADMIN_SISTEMA") ? List.of("usuario.ver") : List.of());
        }
    });

    /** Persona VENDEDOR activa con su acceso vigente. */
    private AccesoFirmado vendedorVigente(UUID usuario) {
        cuentas.put(usuario, new EstadoIdentidadPort.Cuenta("ACTIVO", "carlos@ejemplo.com"));
        roles.put(usuario, List.of("VENDEDOR"));
        AccesoFirmado acceso = firma.firmar(new ClaimsAcceso(usuario, "carlos@ejemplo.com", List.of("VENDEDOR"), List.of()));
        accesosVigentes.add(acceso.jti());
        return acceso;
    }

    @Test
    void unAccesoVigenteSinCambiosEstaActivoConLosDatosDeHoy() {
        UUID usuario = UUID.randomUUID();
        AccesoFirmado acceso = vendedorVigente(usuario);

        var resultado = introspeccion.introspeccionar(PRODUCTOS, acceso.token());

        assertThat(resultado).isEqualTo(new ResultadoIntrospeccion.Activa(usuario, "carlos@ejemplo.com",
                List.of("VENDEDOR"), List.of(), acceso.expiraEn()));
    }

    @Test
    void usuarioDesactivadoOBloqueadoNoEsUtilizableAunqueLaFirmaSeaValida() {
        UUID inactivo = UUID.randomUUID();
        UUID bloqueado = UUID.randomUUID();
        UUID borrado = UUID.randomUUID();
        AccesoFirmado deInactivo = vendedorVigente(inactivo);
        AccesoFirmado deBloqueado = vendedorVigente(bloqueado);
        AccesoFirmado deBorrado = vendedorVigente(borrado);
        cuentas.put(inactivo, new EstadoIdentidadPort.Cuenta("INACTIVO", "x@ejemplo.com"));
        cuentas.put(bloqueado, new EstadoIdentidadPort.Cuenta("BLOQUEADO", "y@ejemplo.com"));
        cuentas.remove(borrado);

        assertThat(introspeccion.introspeccionar(PRODUCTOS, deInactivo.token()))
                .isEqualTo(new ResultadoIntrospeccion.Inactiva(Motivo.USUARIO_INACTIVO));
        assertThat(introspeccion.introspeccionar(PRODUCTOS, deBloqueado.token()))
                .isEqualTo(new ResultadoIntrospeccion.Inactiva(Motivo.USUARIO_BLOQUEADO));
        assertThat(introspeccion.introspeccionar(PRODUCTOS, deBorrado.token()))
                .isEqualTo(new ResultadoIntrospeccion.Inactiva(Motivo.USUARIO_INACTIVO));
    }

    @Test
    void sesionCerradaORolesCambiadosDejanElAccesoRevocado() {
        UUID cerrado = UUID.randomUUID();
        UUID ascendido = UUID.randomUUID();
        AccesoFirmado deCerrado = vendedorVigente(cerrado);
        AccesoFirmado deAscendido = vendedorVigente(ascendido);
        accesosVigentes.remove(deCerrado.jti());
        roles.put(ascendido, List.of("ADMIN_SISTEMA", "VENDEDOR"));

        assertThat(introspeccion.introspeccionar(PRODUCTOS, deCerrado.token()))
                .isEqualTo(new ResultadoIntrospeccion.Inactiva(Motivo.TOKEN_REVOCADO));
        assertThat(introspeccion.introspeccionar(PRODUCTOS, deAscendido.token()))
                .isEqualTo(new ResultadoIntrospeccion.Inactiva(Motivo.TOKEN_REVOCADO));
    }

    @Test
    void vencidoMalFirmadoODeServicioSeDistinguen() {
        Instant haceUnSegundo = Instant.now().minusSeconds(1);
        String vencido = TokensDePrueba.firmarRs256(TokensDePrueba.accesoValido(ISSUER)
                .issueTime(Date.from(haceUnSegundo.minusSeconds(900))).expirationTime(Date.from(haceUnSegundo)).build());
        String deServicio = firma.firmarServicio(new ClaimsServicio("modulo-ventas", List.of("api-seguridad"),
                List.of("usuarios:leer"))).token();

        assertThat(introspeccion.introspeccionar(PRODUCTOS, vencido))
                .isEqualTo(new ResultadoIntrospeccion.Inactiva(Motivo.TOKEN_VENCIDO));
        assertThat(introspeccion.introspeccionar(PRODUCTOS, "eyJhbGciOiJub25lIn0.e30."))
                .isEqualTo(new ResultadoIntrospeccion.Inactiva(Motivo.FIRMA_INVALIDA));
        assertThat(introspeccion.introspeccionar(PRODUCTOS, deServicio))
                .isEqualTo(new ResultadoIntrospeccion.Inactiva(Motivo.FIRMA_INVALIDA));
    }
}
