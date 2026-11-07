package com.marketplace.auth_service.direccion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.direccion.application.port.out.DireccionesPersistidasPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsultaDireccionesServiceTest {
    private final DireccionesPersistidasPort persistencia = mock(DireccionesPersistidasPort.class);
    private final ConsultaDireccionesService servicio = new ConsultaDireccionesService(persistencia);

    private static DireccionDatos direccion(String etiqueta, boolean predeterminada) {
        return new DireccionDatos(UUID.randomUUID(), etiqueta, "Lima", "Lima", "Miraflores", "Av. Larco 123", null,
                predeterminada);
    }

    @Test void consultaPorUsuarioDelegaEnPersistenciaYNoExponeEntidades() {
        UUID usuario = UUID.randomUUID();
        DireccionDatos casa = direccion("Casa", true);
        when(persistencia.listar(usuario)).thenReturn(List.of(casa));

        assertThat(servicio.consultar(usuario)).containsExactly(casa);
        verify(persistencia).listar(usuario);
    }

    @Test void consultaSinDireccionesDevuelveListaVacia() {
        UUID usuario = UUID.randomUUID();
        when(persistencia.listar(usuario)).thenReturn(List.of());
        assertThat(servicio.consultar(usuario)).isEmpty();
    }

    @Test void respetaElOrdenQueDevuelveLaPersistencia() {
        UUID usuario = UUID.randomUUID();
        DireccionDatos casa = direccion("Casa", true);
        DireccionDatos trabajo = direccion("Trabajo", false);
        when(persistencia.listar(usuario)).thenReturn(List.of(casa, trabajo));

        assertThat(servicio.consultar(usuario)).containsExactly(casa, trabajo);
    }

    @Test void laListaDevueltaNoSePuedeModificarNiAfectaALaFuente() {
        UUID usuario = UUID.randomUUID();
        List<DireccionDatos> fuente = new ArrayList<>(List.of(direccion("Casa", true)));
        when(persistencia.listar(usuario)).thenReturn(fuente);

        List<DireccionDatos> resultado = servicio.consultar(usuario);
        fuente.clear();

        assertThat(resultado).hasSize(1);
        assertThatThrownBy(resultado::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void rechazaIdentificadorNulo() {
        assertThatThrownBy(() -> servicio.consultar(null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(persistencia);
    }
}
