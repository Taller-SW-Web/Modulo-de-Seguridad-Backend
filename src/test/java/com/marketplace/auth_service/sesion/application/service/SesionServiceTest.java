package com.marketplace.auth_service.sesion.application.service;

import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.dto.CierreResultado;
import com.marketplace.auth_service.sesion.application.dto.ContextoSesion;
import com.marketplace.auth_service.sesion.application.dto.DatosAcceso;
import com.marketplace.auth_service.sesion.application.dto.DatosIdentidad;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.MotivoRechazo;
import com.marketplace.auth_service.sesion.application.dto.RefrescoGenerado;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.dto.TokenAccesoFirmado;
import com.marketplace.auth_service.sesion.application.dto.UsuarioAutenticado;
import com.marketplace.auth_service.sesion.application.port.out.CriptografiaSesionPort;
import com.marketplace.auth_service.sesion.application.port.out.EfectosCierrePort;
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
import java.util.ArrayList;
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
    private EfectosCierreRegistrados efectos;
    private SesionService servicio;
    private UUID usuario;
    private IdentidadAutorizada identidad;

    @BeforeEach
    void base() {
        persistencia = new Memoria();
        firmador = new FirmadorFijo();
        efectos = new EfectosCierreRegistrados();
        servicio = new SesionService(persistencia, new CriptoFija(), firmador,
                u -> new DatosIdentidad(u, "c@ejemplo.test", "Cliente Prueba",
                        List.of("CLIENTE"), List.of(), true),
                efectos, RELOJ);
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

        var despues = new SesionService(persistencia, new CriptoFija(), firmador,
                u -> new DatosIdentidad(u, "c@ejemplo.test", "Cliente Prueba",
                        List.of("CLIENTE"), List.of(), true),
                efectos, Clock.fixed(AHORA.plusSeconds(901), ZoneOffset.UTC));
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

    @Test
    void renovarRotaYEmiteNuevoPar() {
        servicio.abrir(usuario, identidad, contexto());

        var resultado = servicio.renovar(new SecretoRefresco(SECRETO));

        assertThat(resultado).isInstanceOf(RenovacionResultado.Renovada.class);
        var renovada = (RenovacionResultado.Renovada) resultado;
        assertThat(renovada.sesion().refreshToken()).isNotEqualTo(SECRETO);
        assertThat(renovada.sesion().expiresIn()).isEqualTo(900);
        assertThat(persistencia.tokens.values().stream().filter(TokenRefresco::usado)).hasSize(1);
        assertThat(persistencia.tokens).hasSize(2);
        assertThat(persistencia.accesos).hasSize(1);
    }

    @Test
    void renovarDesconocidoNoMuta() {
        assertThat(servicio.renovar(new SecretoRefresco("desconocido")))
                .isEqualTo(new RenovacionResultado.Rechazada(MotivoRechazo.REFRESCO_DESCONOCIDO, null, null));
        assertThat(persistencia.tokens).isEmpty();
    }

    @Test
    void renovarVencidoNoRota() {
        UUID familia = UUID.randomUUID();
        persistencia.guardarFamilia(SesionFamilia.nueva(familia, usuario, AHORA.minusSeconds(8 * 24 * 3600)));
        persistencia.guardarToken(new TokenRefresco(UUID.randomUUID(), usuario, familia, "f".repeat(64),
                false, false, AHORA.minusSeconds(3600), AHORA.minusSeconds(8 * 24 * 3600)));
        assertThat(servicio.renovar(new SecretoRefresco("cualquiera")))
                .isEqualTo(new RenovacionResultado.Rechazada(MotivoRechazo.REFRESCO_VENCIDO, usuario, familia));
        assertThat(persistencia.tokens.values().stream().filter(TokenRefresco::usado)).isEmpty();
    }

    @Test
    void renovarRevocadoNoRota() {
        UUID familia = UUID.randomUUID();
        persistencia.guardarFamilia(SesionFamilia.nueva(familia, usuario, AHORA));
        persistencia.guardarToken(new TokenRefresco(UUID.randomUUID(), usuario, familia, "f".repeat(64),
                true, false, AHORA.plusSeconds(3600), AHORA));
        assertThat(servicio.renovar(new SecretoRefresco("cualquiera")))
                .isEqualTo(new RenovacionResultado.Rechazada(MotivoRechazo.REFRESCO_REVOCADO, usuario, familia));
        assertThat(persistencia.tokens).hasSize(1);
    }

    @Test
    void reusoRevocaSuFamiliaIncluidoElSucesor() {
        servicio.abrir(usuario, identidad, contexto());
        UUID familia = persistencia.familias.values().iterator().next().id();
        assertThat(servicio.renovar(new SecretoRefresco(SECRETO)))
                .isInstanceOf(RenovacionResultado.Renovada.class);

        var resultado = servicio.renovar(new SecretoRefresco(SECRETO));

        assertThat(resultado).isEqualTo(new RenovacionResultado.Rechazada(
                MotivoRechazo.REUSO_DETECTADO, usuario, familia));
        assertThat(((RenovacionResultado.Rechazada) resultado).revocoFamilia()).isTrue();
        assertThat(persistencia.familias.get(familia).revocada()).isTrue();
        assertThat(persistencia.tokens.values().stream()
                .filter(t -> t.familiaId().equals(familia))).allMatch(TokenRefresco::revocado);
    }

    @Test
    void cuentaNoDisponibleNoRota() {        servicio.abrir(usuario, identidad, contexto());
        var sinCuenta = new SesionService(persistencia, new CriptoFija(), firmador,
                u -> new DatosIdentidad(u, "c@ejemplo.test", "Cliente Prueba",
                        List.of("CLIENTE"), List.of(), false),
                efectos, RELOJ);
        assertThat(sinCuenta.renovar(new SecretoRefresco(SECRETO)))
                .extracting(r -> ((RenovacionResultado.Rechazada) r).motivo())
                .isEqualTo(MotivoRechazo.CUENTA_NO_DISPONIBLE);
        assertThat(persistencia.tokens.values().stream().filter(TokenRefresco::usado)).isEmpty();
    }

    @Test
    void cerrarRevocaLaFamiliaYAuditaElHechoConElContexto() {
        servicio.abrir(usuario, identidad, contexto());
        UUID familia = persistencia.familias.values().iterator().next().id();

        assertThat(servicio.cerrar(new SecretoRefresco(SECRETO), contexto()))
                .isEqualTo(new CierreResultado.Cerrada(usuario, familia));
        assertThat(persistencia.familias.get(familia).revocada()).isTrue();
        assertThat(persistencia.tokens.values()).allMatch(TokenRefresco::revocado);

        // FEAT-12-T2: el hecho nace solo desde Cerrada, con la identidad real.
        assertThat(efectos.registros).hasSize(1);
        var registro = efectos.registros.get(0);
        assertThat(registro.usuarioId()).isEqualTo(usuario);
        assertThat(registro.familiaId()).isEqualTo(familia);
        assertThat(registro.ahora()).isEqualTo(AHORA);
        assertThat(registro.contexto().ip()).isEqualTo("127.0.0.1");
    }

    @Test
    void cerrarRepetidoYDesconocidoSonYaCerradaSinAuditar() {
        servicio.abrir(usuario, identidad, contexto());
        servicio.cerrar(new SecretoRefresco(SECRETO), contexto());
        assertThat(servicio.cerrar(new SecretoRefresco(SECRETO), contexto()))
                .isEqualTo(new CierreResultado.YaCerrada());
        assertThat(servicio.cerrar(new SecretoRefresco("inexistente"), contexto()))
                .isEqualTo(new CierreResultado.YaCerrada());

        // Un logout repetido no duplica el hecho y un refresco desconocido
        // jamás audita una identidad inventada.
        assertThat(efectos.registros).hasSize(1);
    }

    @Test
    void cerrarConRefrescoVencidoCierraIgual() {
        UUID familia = UUID.randomUUID();
        persistencia.guardarFamilia(SesionFamilia.nueva(familia, usuario, AHORA.minusSeconds(8 * 24 * 3600)));
        persistencia.guardarToken(new TokenRefresco(UUID.randomUUID(), usuario, familia, "f".repeat(64),
                false, false, AHORA.minusSeconds(3600), AHORA.minusSeconds(8 * 24 * 3600)));
        assertThat(servicio.cerrar(new SecretoRefresco("cualquiera"), contexto()))
                .isEqualTo(new CierreResultado.Cerrada(usuario, familia));
        assertThat(persistencia.familias.get(familia).revocada()).isTrue();
        assertThat(efectos.registros).hasSize(1);
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

    static class EfectosCierreRegistrados implements EfectosCierrePort {
        record Llamado(UUID usuarioId, UUID familiaId, ContextoPeticion contexto, Instant ahora) { }
        final List<Llamado> registros = new ArrayList<>();

        @Override
        public void auditarCierre(UUID usuarioId, UUID familiaId, ContextoPeticion contexto, Instant ahora) {
            registros.add(new Llamado(usuarioId, familiaId, contexto, ahora));
        }
    }

    static class CriptoFija implements CriptografiaSesionPort {
        private int generados = 0;
        private final Map<String, String> hashes = new HashMap<>();

        @Override public RefrescoGenerado generarRefresco() {
            generados++;
            if (generados == 1) {
                hashes.put(SECRETO, HASH);
                return new RefrescoGenerado(SECRETO, HASH);
            }
            String secreto = "secreto-" + generados + "-de-32-bytes-000000";
            String hash = String.format("%064x", generados);
            hashes.put(secreto, hash);
            return new RefrescoGenerado(secreto, hash);
        }

        @Override public String hash(String secreto) {
            if (SECRETO.equals(secreto)) {
                return HASH;
            }
            return hashes.getOrDefault(secreto, "f".repeat(64));
        }
    }
}
