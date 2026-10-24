package com.marketplace.auth_service.direccion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.direccion.DoblesDirecciones;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort.DatosNuevaDireccion;
import com.marketplace.auth_service.direccion.domain.model.DireccionNoEncontradaException;
import com.marketplace.auth_service.direccion.domain.model.DireccionNoPermitidaException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
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
    private final DireccionesService servicio = new DireccionesService(persistencia, cuentas, eventos);
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
        servicio.crear(actor, titular, datos("Casa", null));

        assertThat(orden).containsExactly("bloquear", "crear");
        assertThat(eventos.publicados).containsExactly(titular);
    }

    @Test
    void laPrimeraDireccionEsPredeterminadaAunqueNoLoPida() {
        assertThat(servicio.crear(actor, titular, datos("Casa", false)).esPredeterminada()).isTrue();
    }

    @Test
    void unaNuevaPredeterminadaDejaSinMarcaALaAnterior() {
        servicio.crear(actor, titular, datos("Casa", true));
        servicio.crear(actor, titular, datos("Trabajo", true));

        var lista = servicio.listar(actor, titular);
        assertThat(lista).hasSize(2);
        assertThat(lista.stream().filter(d -> d.esPredeterminada())).extracting(d -> d.etiqueta())
                .containsExactly("Trabajo");
        assertThat(lista.get(0).etiqueta()).as("la predeterminada va primero").isEqualTo("Trabajo");
    }

    @Test
    void unaSegundaSinPedirlaNoQuitaLaPredeterminada() {
        servicio.crear(actor, titular, datos("Casa", null));
        servicio.crear(actor, titular, datos("Trabajo", false));

        assertThat(servicio.listar(actor, titular).stream().filter(d -> d.esPredeterminada()))
                .extracting(d -> d.etiqueta()).containsExactly("Casa");
    }

    @Test
    void otroUsuarioRecibeDenegadoSinConsultarNadaNiPublicar() {
        UUID ajeno = UUID.randomUUID();
        cuentas.existentes.add(ajeno);

        assertThatThrownBy(() -> servicio.listar(actor, ajeno)).isInstanceOf(DireccionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.crear(actor, ajeno, datos("Casa", null)))
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
        assertThatThrownBy(() -> servicio.crear(actor, titular, datos("Casa", null)))
                .isInstanceOf(DireccionNoEncontradaException.class);
        assertThat(persistencia.llamadas).isZero();
        assertThat(eventos.publicados).isEmpty();
    }
}
