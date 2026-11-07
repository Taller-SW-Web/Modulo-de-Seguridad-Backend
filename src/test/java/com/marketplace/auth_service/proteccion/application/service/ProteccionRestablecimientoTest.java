package com.marketplace.auth_service.proteccion.application.service;

import static org.mockito.Mockito.*;

import com.marketplace.auth_service.proteccion.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.proteccion.application.port.out.*;
import com.marketplace.auth_service.proteccion.domain.model.Bloqueo;
import com.marketplace.auth_service.proteccion.domain.model.RachaFallo;
import com.marketplace.auth_service.proteccion.domain.model.TipoBloqueo;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProteccionRestablecimientoTest {
    private static final UUID USUARIO=UUID.randomUUID();
    private static final Instant AHORA=Instant.parse("2026-10-22T10:00:00Z");
    private static final ContextoPeticion CONTEXTO=new ContextoPeticion("127.0.0.1","test",UUID.randomUUID());
    private final PersistenciaProteccionPort persistencia=mock(PersistenciaProteccionPort.class);
    private final HuellaProteccionPort huellas=mock(HuellaProteccionPort.class);
    private final CuentasProteccionPort cuentas=mock(CuentasProteccionPort.class);
    private final TokensProteccionPort tokens=mock(TokensProteccionPort.class);
    private final EfectosProteccionPort efectos=mock(EfectosProteccionPort.class);
    private ProteccionService servicio;

    @BeforeEach void preparar() {
        when(cuentas.leer(USUARIO)).thenReturn(Optional.of(new CuentaSnapshot(USUARIO,"persona@example.test","ACTIVO",2,5)));
        when(huellas.claveActualId()).thenReturn("key-1");
        servicio=new ProteccionService(persistencia,huellas,cuentas,tokens,efectos,null,
                Clock.fixed(AHORA,ZoneOffset.UTC));
    }

    @Test void restablecimientoQuitaSoloBloqueoAutomaticoReiniciaIntentosYPreservaRacha() {
        var bloqueo=new Bloqueo(USUARIO,TipoBloqueo.AUTOMATICO,AHORA.plusSeconds(60),null,AHORA.minusSeconds(60),UUID.randomUUID());
        when(persistencia.bloquearBloqueo(USUARIO)).thenReturn(Optional.of(bloqueo));
        when(persistencia.bloquearRacha(USUARIO)).thenReturn(Optional.of(
                new RachaFallo(USUARIO,UUID.randomUUID(),"key-1",AHORA.minusSeconds(300))));
        when(persistencia.eliminarBloqueo(USUARIO,bloqueo.revision())).thenReturn(true);

        servicio.prepararRestablecimiento(USUARIO);
        servicio.restablecerPorRecuperacion(USUARIO,CONTEXTO,AHORA);

        verify(persistencia).eliminarBloqueo(USUARIO,bloqueo.revision());
        verify(persistencia).reiniciarRacha(any());
        verify(cuentas).guardarContadores(USUARIO,2,0);
        verify(efectos).desbloqueadaPorRestablecimiento(any(),eq(bloqueo.revision()),eq(CONTEXTO),eq(AHORA));
    }

    @Test void mantieneBloqueoManualYReiniciaContadorFallido() {
        var bloqueo=new Bloqueo(USUARIO,TipoBloqueo.MANUAL,null,"Revisión administrativa",AHORA.minusSeconds(60),UUID.randomUUID());
        when(persistencia.bloquearBloqueo(USUARIO)).thenReturn(Optional.of(bloqueo));
        when(persistencia.bloquearRacha(USUARIO)).thenReturn(Optional.empty());

        servicio.prepararRestablecimiento(USUARIO);
        servicio.restablecerPorRecuperacion(USUARIO,CONTEXTO,AHORA);

        verify(persistencia,never()).eliminarBloqueo(any(),any());
        verify(efectos,never()).desbloqueadaPorRestablecimiento(any(),any(),any(),any());
        verify(persistencia).crearRacha(any());
        verify(cuentas).guardarContadores(USUARIO,2,0);
    }
}
