package com.marketplace.auth_service.sesion.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.sesion.application.dto.CierreResultado;
import com.marketplace.auth_service.sesion.application.dto.ContextoSesion;
import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.dto.CuentaLogin;
import com.marketplace.auth_service.sesion.application.dto.DesafioEmitido;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.PreverificacionLogin;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.sesion.application.dto.RevisionLogin;
import com.marketplace.auth_service.sesion.application.dto.RolesLogin;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.dto.UsuarioAutenticado;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.CapacidadesLoginPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ramas del coordinador con dobles de las capacidades (aislado; la composición real va en PG). */
class LoginServiceTest {

    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-00000000f00a");
    private static final String CORREO = "cliente.a@fixtures.g7.test";
    private static final String CLAVE = "Zorzal-Andino-2026!";
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("10.0.0.1", "jdk", UUID.randomUUID());
    private static final RevisionLogin REVISION = new RevisionLogin(ID, UUID.randomUUID(), 3);

    private Doble capacidades;
    private SesionesDoble sesiones;
    private int transacciones;
    private LoginService servicio;

    @BeforeEach
    void preparar() {
        capacidades = new Doble();
        sesiones = new SesionesDoble();
        transacciones = 0;
        servicio = new LoginService(capacidades, sesiones, new com.marketplace.auth_service.sesion.application.port.out.TransaccionLoginPort() {
            @Override
            public <T> T ejecutar(java.util.function.Supplier<T> trabajo) {
                transacciones++;
                capacidades.eventos.add("tx");
                return trabajo.get();
            }
        }, Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void clienteSinMfaObtieneSesionConRolesYPermisosYReiniciaProteccion() {
        var resultado = servicio.iniciar("  Cliente.A@Fixtures.G7.test ", CLAVE, CONTEXTO);

        assertThat(resultado).isInstanceOf(ResultadoLogin.Iniciada.class);
        assertThat(sesiones.identidad.roles()).containsExactly("CLIENTE");
        assertThat(sesiones.identidad.correo()).isEqualTo(CORREO);
        assertThat(capacidades.eventos).containsSubsequence("buscar:" + CORREO, "preverificar", "tx",
                "bloquearCuenta", "confirmar", "caducidad", "completar");
        assertThat(capacidades.eventos).doesNotContain("guardAdmin", "fallo");
    }

    @Test
    void contrasenaErroneaRegistraElFalloConGuardAntesDeLaCuentaYDevuelveElMismo401() {
        capacidades.coincide = false;

        var resultado = servicio.iniciar(CORREO, "mala", CONTEXTO);

        assertThat(resultado).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);
        assertThat(capacidades.eventos).containsSubsequence("guardAdmin", "bloquearCuenta", "fallo:" + ID);
        assertThat(sesiones.abiertas).isZero();
    }

    @Test
    void correoInexistenteHaceComparacionEquivalenteYRegistraElIntento() {
        capacidades.cuenta = null;

        var resultado = servicio.iniciar("nadie@g7.test", CLAVE, CONTEXTO);

        assertThat(resultado).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);
        assertThat(capacidades.eventos).contains("preverificar", "fallo:sin-cuenta");
        assertThat(capacidades.eventos).doesNotContain("bloquearCuenta");
    }

    @Test
    void cuentaNoDisponibleOBloqueadaConContrasenaCorrectaEsElMismo401SinSesion() {
        capacidades.cuenta = new CuentaLogin(ID, CORREO, "Ana Prueba", false, false);
        assertThat(servicio.iniciar(CORREO, CLAVE, CONTEXTO)).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);

        capacidades.cuenta = new CuentaLogin(ID, CORREO, "Ana Prueba", true, false);
        capacidades.proteccionPermite = false;
        assertThat(servicio.iniciar(CORREO, CLAVE, CONTEXTO)).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);
        assertThat(sesiones.abiertas).isZero();
        assertThat(capacidades.eventos).doesNotContain("completar");
    }

    @Test
    void mfaEfectivoDevuelveSoloDesafioSinFamiliaNiReinicioDeRacha() {
        capacidades.mfa = true;

        var resultado = servicio.iniciar(CORREO, CLAVE, CONTEXTO);

        assertThat(resultado).isInstanceOf(ResultadoLogin.DesafioRequerido.class);
        assertThat(sesiones.abiertas).isZero();
        assertThat(capacidades.eventos).contains("desafio").doesNotContain("completar", "confirmar");
    }

    @Test
    void revisionCambiadaSeRepiteUnaVezYAlaSegundaRechaza() {
        capacidades.confirmaciones = new ArrayList<>(List.of(false, true));
        assertThat(servicio.iniciar(CORREO, CLAVE, CONTEXTO)).isInstanceOf(ResultadoLogin.Iniciada.class);
        assertThat(transacciones).isEqualTo(2);

        preparar();
        capacidades.confirmaciones = new ArrayList<>(List.of(false, false));
        assertThat(servicio.iniciar(CORREO, CLAVE, CONTEXTO)).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);
        assertThat(transacciones).isEqualTo(2);
        assertThat(sesiones.abiertas).isZero();
    }

    @Test
    void correoCambiadoEntreBusquedaYLockSeRepite() {
        capacidades.correoAlBloquear = "otro@g7.test";
        assertThat(servicio.iniciar(CORREO, CLAVE, CONTEXTO)).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);
        assertThat(transacciones).isEqualTo(2);
        assertThat(capacidades.eventos).doesNotContain("fallo:" + ID);
    }

    @Test
    void contrasenaCaducadaDa403AlCompletarSinAbrirSesion() {
        capacidades.caducada = true;
        assertThat(servicio.iniciar(CORREO, CLAVE, CONTEXTO)).isEqualTo(ResultadoLogin.Rechazo.CONTRASENA_CADUCADA);
        assertThat(sesiones.abiertas).isZero();
        assertThat(capacidades.eventos).doesNotContain("completar");
    }

    @Test
    void cuerpoVacioEsCredencialesInvalidasSinBuscarCuenta() {
        assertThat(servicio.iniciar(null, null, CONTEXTO)).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);
        assertThat(capacidades.eventos).noneMatch(e -> e.startsWith("buscar"));
    }

    private static final class Doble implements CapacidadesLoginPort {
        final List<String> eventos = new ArrayList<>();
        CuentaLogin cuenta = new CuentaLogin(ID, CORREO, "Ana Prueba", true, false);
        String correoAlBloquear;
        boolean coincide = true;
        boolean proteccionPermite = true;
        boolean mfa;
        boolean caducada;
        List<Boolean> confirmaciones = new ArrayList<>(List.of(true, true));

        @Override public Optional<CuentaLogin> buscarCuenta(String correo) {
            eventos.add("buscar:" + correo);
            return Optional.ofNullable(cuenta);
        }
        @Override public PreverificacionLogin preverificar(Optional<UUID> usuarioId, String contrasena) {
            eventos.add("preverificar");
            return new PreverificacionLogin(usuarioId.isPresent() && coincide, usuarioId.map(u -> REVISION));
        }
        @Override public void bloquearCatalogoAdministrador() { eventos.add("guardAdmin"); }
        @Override public CuentaLogin bloquearCuenta(UUID usuarioId) {
            eventos.add("bloquearCuenta");
            return correoAlBloquear == null ? cuenta
                    : new CuentaLogin(cuenta.id(), correoAlBloquear, cuenta.nombreCompleto(), true, false);
        }
        @Override public boolean proteccionPermiteLogin(UUID usuarioId, Instant ahora) { return proteccionPermite; }
        @Override public void registrarFallo(Optional<UUID> usuarioId, String correo, String c, ContextoPeticion x) {
            eventos.add("fallo:" + usuarioId.map(UUID::toString).orElse("sin-cuenta"));
        }
        @Override public boolean confirmarRevision(RevisionLogin revision) {
            eventos.add("confirmar");
            return confirmaciones.remove(0);
        }
        @Override public RolesLogin resolverRoles(UUID usuarioId) {
            return new RolesLogin(List.of("CLIENTE"), List.of());
        }
        @Override public boolean mfaEfectivo(boolean voluntario, RolesLogin roles) { return mfa; }
        @Override public Optional<DesafioEmitido> crearDesafio(UUID usuarioId, RevisionLogin revision) {
            eventos.add("desafio");
            return Optional.of(new DesafioEmitido("desafio-secreto", "EMAIL", 300));
        }
        @Override public boolean contrasenaCaducada(UUID usuarioId, RolesLogin roles, Instant ahora) {
            eventos.add("caducidad");
            return caducada;
        }
        @Override public void completarLogin(UUID usuarioId) { eventos.add("completar"); }
    }

    private static final class SesionesDoble implements SesionesPort {
        int abiertas;
        IdentidadAutorizada identidad;

        @Override public SesionIniciada abrir(UUID usuarioId, IdentidadAutorizada identidad, ContextoPeticion contexto) {
            abiertas++;
            this.identidad = identidad;
            Instant ahora = Instant.parse("2026-10-17T15:00:00Z");
            return new SesionIniciada("acceso", "refresco", 900, ahora.plusSeconds(900), ahora.plusSeconds(604800),
                    new UsuarioAutenticado(usuarioId, identidad.nombreCompleto(), identidad.correo(), identidad.roles()));
        }
        @Override public ContextoSesion resolverAcceso(AccesoVerificado acceso) { throw new UnsupportedOperationException(); }
        @Override public RenovacionResultado renovar(SecretoRefresco secreto) { throw new UnsupportedOperationException(); }
        @Override public CierreResultado cerrar(SecretoRefresco secreto) { throw new UnsupportedOperationException(); }
        @Override public void revocarTodas(UUID usuarioId) { throw new UnsupportedOperationException(); }
        @Override public void revocarOtras(UUID usuarioId, UUID familiaActual) { throw new UnsupportedOperationException(); }
    }
}
