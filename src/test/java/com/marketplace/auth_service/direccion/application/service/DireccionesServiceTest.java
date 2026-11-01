package com.marketplace.auth_service.direccion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.direccion.DoblesDirecciones;
import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort.DatosNuevaDireccion;
import com.marketplace.auth_service.direccion.domain.model.DireccionNoEncontradaException;
import com.marketplace.auth_service.direccion.domain.model.DireccionNoPermitidaException;
import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reglas de FEAT-04-T1 sin Spring ni base de datos: titularidad, existencia, bloqueo y evento. */
class DireccionesServiceTest {
    private final List<String> orden = new ArrayList<>();
    private final DoblesDirecciones.Persistencia persistencia = new DoblesDirecciones.Persistencia(orden);
    private final DoblesDirecciones.Cuentas cuentas = new DoblesDirecciones.Cuentas(orden);
    private final DoblesDirecciones.Eventos eventos = new DoblesDirecciones.Eventos();
    private final DoblesDirecciones.Autorizacion autorizacion = new DoblesDirecciones.Autorizacion();
    private final DoblesDirecciones.Auditoria auditoria = new DoblesDirecciones.Auditoria();
    private final DireccionesService servicio =
            new DireccionesService(persistencia, cuentas, eventos, autorizacion, auditoria);
    private static final ContextoPeticion CTX = new ContextoPeticion("203.0.113.7", "JUnit", UUID.randomUUID());
    private final UUID titular = UUID.randomUUID();
    private ActorUsuario actor;

    @BeforeEach
    void preparar() {
        actor = new ActorUsuario(titular, UUID.randomUUID(), Set.of("CLIENTE"), Set.of());
        cuentas.existentes.add(titular);
    }

    private static DatosNuevaDireccion datos(String etiqueta, Boolean predeterminada) {
        return new DatosNuevaDireccion(etiqueta, "Lima", "Lima", "Miraflores", "Av. Larco 123", null, predeterminada);
    }

    @Test
    void elTitularSinDireccionesObtieneUnaListaVacia() {
        assertThat(servicio.listar(actor, titular)).isEmpty();
    }

    @Test
    void crearBloqueaLaCuentaAntesDeEscribirYPublicaUnSoloEvento() {
        servicio.crear(actor, titular, datos("Casa", null), CTX);

        assertThat(orden).containsExactly("bloquear", "crear");
        assertThat(eventos.publicados).containsExactly(titular);
    }

    @Test
    void laPrimeraDireccionEsPredeterminadaAunqueNoLoPida() {
        assertThat(servicio.crear(actor, titular, datos("Casa", false), CTX).esPredeterminada()).isTrue();
    }

    @Test
    void unaNuevaPredeterminadaDejaSinMarcaALaAnterior() {
        servicio.crear(actor, titular, datos("Casa", true), CTX);
        servicio.crear(actor, titular, datos("Trabajo", true), CTX);

        var lista = servicio.listar(actor, titular);
        assertThat(lista).hasSize(2);
        assertThat(lista.stream().filter(d -> d.esPredeterminada())).extracting(d -> d.etiqueta())
                .containsExactly("Trabajo");
        assertThat(lista.get(0).etiqueta()).as("la predeterminada va primero").isEqualTo("Trabajo");
    }

    @Test
    void unaSegundaSinPedirlaNoQuitaLaPredeterminada() {
        servicio.crear(actor, titular, datos("Casa", null), CTX);
        servicio.crear(actor, titular, datos("Trabajo", false), CTX);

        assertThat(servicio.listar(actor, titular).stream().filter(d -> d.esPredeterminada()))
                .extracting(d -> d.etiqueta()).containsExactly("Casa");
    }

    @Test
    void otroUsuarioRecibeDenegadoSinConsultarNadaNiPublicar() {
        UUID ajeno = UUID.randomUUID();
        cuentas.existentes.add(ajeno);

        assertThatThrownBy(() -> servicio.listar(actor, ajeno)).isInstanceOf(DireccionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.crear(actor, ajeno, datos("Casa", null), CTX))
                .isInstanceOf(DireccionNoPermitidaException.class);

        assertThat(cuentas.consultas).as("no revela si la cuenta existe").isZero();
        assertThat(cuentas.bloqueos).isZero();
        assertThat(persistencia.llamadas).isZero();
        assertThat(eventos.publicados).isEmpty();
    }

    @Test
    void ajenoExistenteYAjenoInexistenteSeResuelvenIgual() {
        UUID existente = UUID.randomUUID();
        cuentas.existentes.add(existente);

        assertThatThrownBy(() -> servicio.listar(actor, existente)).isExactlyInstanceOf(DireccionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.listar(actor, UUID.randomUUID()))
                .isExactlyInstanceOf(DireccionNoPermitidaException.class);
    }

    @Test
    void sinActorSeDeniega() {
        assertThatThrownBy(() -> servicio.listar(null, titular)).isInstanceOf(DireccionNoPermitidaException.class);
    }

    @Test
    void unaCuentaPropiaQueYaNoExisteEsNoEncontradaYNoEscribe() {
        cuentas.existentes.clear();

        assertThatThrownBy(() -> servicio.listar(actor, titular)).isInstanceOf(DireccionNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.crear(actor, titular, datos("Casa", null), CTX))
                .isInstanceOf(DireccionNoEncontradaException.class);
        assertThat(persistencia.llamadas).isZero();
        assertThat(eventos.publicados).isEmpty();
    }

    // --- Auditoría no crítica (RF-16.10) ---

    @Test
    void crearRegistraUnaAuditoriaConActorYObjetivoYNadaMas() {
        servicio.crear(actor, titular, datos("Casa", null), CTX);

        assertThat(auditoria.registradas).hasSize(1);
        var llamada = auditoria.registradas.get(0);
        assertThat(llamada.actor()).isEqualTo(titular);
        assertThat(llamada.objetivo()).isEqualTo(titular);
        assertThat(llamada.contexto()).isSameAs(CTX);
    }

    @Test
    void sinAltaNoHayAuditoriaDeExito() {
        UUID ajeno = UUID.randomUUID();
        cuentas.existentes.add(ajeno);
        assertThatThrownBy(() -> servicio.crear(actor, ajeno, datos("Casa", null), CTX))
                .isInstanceOf(DireccionNoPermitidaException.class);
        cuentas.existentes.clear();
        assertThatThrownBy(() -> servicio.crear(actor, titular, datos("Casa", null), CTX))
                .isInstanceOf(DireccionNoEncontradaException.class);

        assertThat(auditoria.registradas).isEmpty();
    }

    // --- Acceso administrativo del contrato, con permisos vigentes ---

    @Test
    void quienTieneUsuarioVerHoyPuedeListarLasDireccionesDeOtro() {
        UUID soporte = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
        cuentas.existentes.add(cliente);
        autorizacion.conUsuarioVer.add(soporte);

        assertThat(servicio.listar(new ActorUsuario(soporte, UUID.randomUUID(), Set.of(), Set.of()), cliente)).isEmpty();
    }

    @Test
    void unPermisoSoloEnElTokenNoAutorizaPorqueSeRevalidaContraLaBase() {
        UUID soporte = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
        cuentas.existentes.add(cliente);
        var conClaim = new ActorUsuario(soporte, UUID.randomUUID(), Set.of("ADMIN_SISTEMA"),
                Set.of("usuario.ver"));

        assertThatThrownBy(() -> servicio.listar(conClaim, cliente)).isInstanceOf(DireccionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.crear(conClaim, cliente, datos("Casa", null), CTX))
                .isInstanceOf(DireccionNoPermitidaException.class);
        assertThat(persistencia.llamadas).isZero();
        assertThat(eventos.publicados).isEmpty();
        assertThat(auditoria.registradas).isEmpty();
    }

    @Test
    void usuarioVerPermiteLeerPeroNoAnadir() {
        UUID soporte = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
        cuentas.existentes.add(cliente);
        autorizacion.conUsuarioVer.add(soporte);
        var actorSoporte = new ActorUsuario(soporte, UUID.randomUUID(), Set.of(), Set.of());

        assertThatThrownBy(() -> servicio.crear(actorSoporte, cliente, datos("Casa", null), CTX))
                .isInstanceOf(DireccionNoPermitidaException.class);
        assertThat(persistencia.llamadas).isZero();
    }

    @Test
    void adminSistemaAnadeEnCuentaAjenaYElEventoYLaAuditoriaApuntanAlObjetivo() {
        UUID admin = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
        cuentas.existentes.add(cliente);
        autorizacion.adminsSistema.add(admin);

        servicio.crear(new ActorUsuario(admin, UUID.randomUUID(), Set.of(), Set.of()), cliente,
                datos("Casa", null), CTX);

        assertThat(servicio.listar(actor, titular)).isEmpty();
        assertThat(eventos.publicados).containsExactly(cliente);
        assertThat(auditoria.registradas).singleElement().satisfies(l -> {
            assertThat(l.actor()).isEqualTo(admin);
            assertThat(l.objetivo()).isEqualTo(cliente);
        });
    }

    @Test
    void adminSistemaSobreCuentaInexistenteEsNoEncontradaSinEscribir() {
        UUID admin = UUID.randomUUID();
        autorizacion.adminsSistema.add(admin);

        assertThatThrownBy(() -> servicio.crear(new ActorUsuario(admin, UUID.randomUUID(), Set.of(), Set.of()),
                UUID.randomUUID(), datos("Casa", null), CTX)).isInstanceOf(DireccionNoEncontradaException.class);
        assertThat(persistencia.llamadas).isZero();
        assertThat(auditoria.registradas).isEmpty();
    }

    // --- Lectura por módulo con token de servicio (SPEC-18 ESC-18.4, FEAT-06-T3) ---

    @Test
    void unModuloConScopeListaSinAutorizarPorRolNiTitularidadYAudita() {
        ActorServicio despacho = new ActorServicio("despacho", UUID.randomUUID(), Set.of("direcciones:leer"));
        servicio.crear(actor, titular, datos("Casa", null), CTX);

        List<DireccionDatos> resultado = servicio.listarParaServicio(despacho, titular);

        assertThat(resultado).hasSize(1);
        assertThat(auditoria.registradasServicio).containsExactly(
                new DoblesDirecciones.Auditoria.LlamadaServicio("despacho", titular));
        assertThat(auditoria.registradas).as("no mezcla con la auditoría de alta del titular").hasSize(1);
    }

    @Test
    void unModuloSobreCuentaInexistenteEsNoEncontradaSinAuditar() {
        ActorServicio despacho = new ActorServicio("despacho", UUID.randomUUID(), Set.of("direcciones:leer"));

        assertThatThrownBy(() -> servicio.listarParaServicio(despacho, UUID.randomUUID()))
                .isInstanceOf(DireccionNoEncontradaException.class);
        assertThat(auditoria.registradasServicio).isEmpty();
    }

    @Test
    void sinActorDeServicioSeRechaza() {
        assertThatThrownBy(() -> servicio.listarParaServicio(null, titular))
                .isInstanceOf(NullPointerException.class);
    }
}
