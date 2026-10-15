package com.marketplace.auth_service.sesion.application.service;

import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.dto.ContextoSesion;
import com.marketplace.auth_service.sesion.application.dto.DatosAcceso;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.RefrescoGenerado;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.dto.TokenAccesoFirmado;
import com.marketplace.auth_service.sesion.application.dto.UsuarioAutenticado;
import com.marketplace.auth_service.sesion.application.port.out.CriptografiaSesionPort;
import com.marketplace.auth_service.sesion.application.port.out.FirmadorAccesoPort;
import com.marketplace.auth_service.sesion.application.port.out.PersistenciaSesionPort;
import com.marketplace.auth_service.sesion.domain.exception.AccesoDesconocidoException;
import com.marketplace.auth_service.sesion.domain.exception.FamiliaRevocadaException;
import com.marketplace.auth_service.sesion.domain.model.AccesoSesion;
import com.marketplace.auth_service.sesion.domain.model.SesionFamilia;
import com.marketplace.auth_service.sesion.domain.model.TokenRefresco;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Núcleo con puertos dobles, sin Spring ni DB (EP-02-T003). */
class SesionServiceTest {
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final String HASH = "a".repeat(64);
    private static final String SECRETO = "secreto-de-32-bytes-para-la-prueba-x";

    private Memoria persistencia;
    private FirmadorFijo firmador;
    private SesionService servicio;
    private UUID usuario;
    private IdentidadAutorizada identidad;

    @BeforeEach
    void base() {
        persistencia = new Memoria();
        firmador = new FirmadorFijo();
        servicio = new SesionService(persistencia, () -> new RefrescoGenerado(SECRETO, HASH), firmador, RELOJ);
        usuario = UUID.randomUUID();
        identidad = new IdentidadAutorizada(usuario, "Cliente Prueba", "c@ejemplo.test",
                List.of("CLIENTE"), List.of());
    }

    private ContextoPeticion contexto() {
        return new ContextoPeticion("127.0.0.1", "prueba", UUID.randomUUID());
    }

    @Test
    void abrirPersisteFamiliaTokenYAcceso() {
        SesionIniciada iniciada = servicio.abrir(usuario, identidad, contexto());

        assertThat(iniciada.refreshToken()).isEqualTo(SECRETO);
        assertThat(iniciada.accessToken()).isEqualTo(FirmadorFijo.TOKEN);
        assertThat(iniciada.expiresIn()).isEqualTo(900);
        assertThat(iniciada.expiraRefrescoEn()).isEqualTo(AHORA.plusSeconds(7 * 24 * 3600));
        assertThat(iniciada.usuario()).isEqualTo(
                new UsuarioAutenticado(usuario, "Cliente Prueba", "c@ejemplo.test", List.of("CLIENTE")));
        assertThat(iniciada.toString()).doesNotContain(SECRETO, FirmadorFijo.TOKEN);

        assertThat(persistencia.familias).hasSize(1);
        SesionFamilia familia = persistencia.familias.values().iterator().next();
        assertThat(familia.usuarioId()).isEqualTo(usuario);
        assertThat(persistencia.tokens).hasSize(1);
        TokenRefresco token = persistencia.tokens.values().iterator().next();
        assertThat(token.hashToken()).isEqualTo(HASH);
        assertThat(token.familiaId()).isEqualTo(familia.id());
        assertThat(persistencia.accesos).hasSize(1);
        AccesoSesion acceso = persistencia.accesos.values().iterator().next();
        assertThat(acceso.jti()).isEqualTo(FirmadorFijo.JTI);
        assertThat(acceso.familiaId()).isEqualTo(familia.id());
        assertThat(firmador.ultimosDatos)
                .isEqualTo(new DatosAcceso(usuario, "c@ejemplo.test", List.of("CLIENTE"), List.of()));
    }

    @Test
    void abrirConIdentidadDeOtroUsuarioFallaSinPersistir() {
        var ajena = new IdentidadAutorizada(UUID.randomUUID(), "Otra", "o@ejemplo.test",
                List.of("CLIENTE"), List.of());
        assertThatThrownBy(() -> servicio.abrir(usuario, ajena, contexto()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(persistencia.familias).isEmpty();
    }

    @Test
    void resolverAccesoDevuelveLaFamiliaPersistida() {
        SesionIniciada iniciada = servicio.abrir(usuario, identidad, contexto());
        UUID familia = persistencia.familias.values().iterator().next().id();

        ContextoSesion contexto = servicio.resolverAcceso(new AccesoVerificado(usuario, FirmadorFijo.JTI));

        assertThat(contexto.usuarioId()).isEqualTo(usuario);
        assertThat(contexto.familiaId()).isEqualTo(familia);
        assertThat(contexto.accessJti()).isEqualTo(FirmadorFijo.JTI);
        assertThat(iniciada.accessToken()).isNotNull();
    }

    @Test
    void jtiDesconocidoDeOtroSubOVencidoNoResuelven() {
        servicio.abrir(usuario, identidad, contexto());
        assertThatThrownBy(() -> servicio.resolverAcceso(new AccesoVerificado(usuario, UUID.randomUUID())))
                .isInstanceOf(AccesoDesconocidoException.class);
        assertThatThrownBy(() -> servicio.resolverAcceso(new AccesoVerificado(UUID.randomUUID(), FirmadorFijo.JTI)))
                .isInstanceOf(AccesoDesconocidoException.class);

        var despues = new SesionService(persistencia, () -> new RefrescoGenerado(SECRETO, HASH), firmador,
                Clock.fixed(AHORA.plusSeconds(901), ZoneOffset.UTC));
        assertThatThrownBy(() -> despues.resolverAcceso(new AccesoVerificado(usuario, FirmadorFijo.JTI)))
                .isInstanceOf(AccesoDesconocidoException.class);
    }

    @Test
    void familiaRevocadaNoResuelve() {
        servicio.abrir(usuario, identidad, contexto());
        servicio.revocarTodas(usuario);
        assertThatThrownBy(() -> servicio.resolverAcceso(new AccesoVerificado(usuario, FirmadorFijo.JTI)))
                .isInstanceOf(FamiliaRevocadaException.class);
    }

    @Test
    void revocarTodasRevocaFamiliasYSusTokens() {
        servicio.abrir(usuario, identidad, contexto());
        servicio.abrir(usuario, identidad, contexto());
        servicio.revocarTodas(usuario);
        assertThat(persistencia.familias.values()).allMatch(SesionFamilia::revocada);
        assertThat(persistencia.tokens.values()).allMatch(TokenRefresco::revocado);
        assertThat(persistencia.familias).hasSize(2);
    }

    @Test
    void revocarOtrasConservaLaActual() {
        servicio.abrir(usuario, identidad, contexto());
        UUID actual = persistencia.familias.values().iterator().next().id();
        servicio.abrir(usuario, identidad, contexto());
        servicio.revocarOtras(usuario, actual);
        assertThat(persistencia.familias.get(actual).revocada()).isFalse();
        assertThat(persistencia.familias.values().stream().filter(f -> !f.id().equals(actual)))
                .allMatch(SesionFamilia::revocada);
        assertThat(persistencia.tokens.values().stream()
                .filter(t -> !t.familiaId().equals(actual))).allMatch(TokenRefresco::revocado);
        assertThat(persistencia.tokens.values().stream()
                .filter(t -> t.familiaId().equals(actual))).noneMatch(TokenRefresco::revocado);
    }

    static class Memoria implements PersistenciaSesionPort {
        final Map<UUID, SesionFamilia> familias = new HashMap<>();
        final Map<UUID, TokenRefresco> tokens = new HashMap<>();
        final Map<UUID, AccesoSesion> accesos = new HashMap<>();

        @Override public void guardarFamilia(SesionFamilia f) { familias.put(f.id(), f); }
        @Override public Optional<SesionFamilia> bloquearFamilia(UUID id) { return Optional.ofNullable(familias.get(id)); }
        @Override public List<SesionFamilia> listarFamiliasDeUsuario(UUID u) {
            return familias.values().stream().filter(f -> f.usuarioId().equals(u))
                    .sorted((a, b) -> a.id().compareTo(b.id())).toList();
        }
        @Override public void guardarToken(TokenRefresco t) { tokens.put(t.jti(), t); }
        @Override public Optional<TokenRefresco> buscarTokenPorHash(String h) {
            return tokens.values().stream().filter(t -> t.hashToken().equals(h)).findFirst();
        }
        @Override public List<TokenRefresco> listarTokensDeFamilia(UUID f) {
            return tokens.values().stream().filter(t -> t.familiaId().equals(f))
                    .sorted((a, b) -> a.jti().compareTo(b.jti())).toList();
        }
        @Override public void guardarAcceso(AccesoSesion a) { accesos.put(a.jti(), a); }
        @Override public Optional<AccesoSesion> buscarAccesoPorJti(UUID j) { return Optional.ofNullable(accesos.get(j)); }
    }

    static class FirmadorFijo implements FirmadorAccesoPort {
        static final String TOKEN = "firmado-de-prueba";
        static final UUID JTI = UUID.fromString("00000000-0000-0000-0000-00000000f111");
        DatosAcceso ultimosDatos;

        @Override public TokenAccesoFirmado firmar(DatosAcceso datos) {
            ultimosDatos = datos;
            return new TokenAccesoFirmado(TOKEN, JTI, AHORA, AHORA.plusSeconds(900));
        }
    }
}
