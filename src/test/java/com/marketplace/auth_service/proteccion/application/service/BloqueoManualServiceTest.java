package com.marketplace.auth_service.proteccion.application.service;

import com.marketplace.auth_service.proteccion.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.proteccion.application.port.out.*;
import com.marketplace.auth_service.proteccion.domain.model.*;
import com.marketplace.auth_service.shared.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class BloqueoManualServiceTest {
    final CuentasProteccionPort cuentas=mock(CuentasProteccionPort.class);
    final PersistenciaProteccionPort filas=mock(PersistenciaProteccionPort.class);
    final TokensProteccionPort tokens=mock(TokensProteccionPort.class);
    final HuellaProteccionPort huellas=mock(HuellaProteccionPort.class);
    final OperacionManualPort operaciones=mock(OperacionManualPort.class);
    final UUID id=UUID.randomUUID();
    final ActorUsuario actor=new ActorUsuario(UUID.randomUUID(),UUID.randomUUID(),Set.of("ADMIN_SISTEMA"),Set.of("cuenta.bloquear"));
    final ContextoPeticion contexto=new ContextoPeticion("127.0.0.1","test",UUID.randomUUID());
    final Instant ahora=Instant.parse("2026-10-23T12:00:00Z");
    final BloqueoManualService servicio=new BloqueoManualService(cuentas,filas,tokens,huellas,operaciones,Clock.fixed(ahora,ZoneOffset.UTC));
    @BeforeEach void preparar() {
        when(operaciones.autorizado(actor)).thenReturn(true);
        when(cuentas.leer(any())).thenAnswer(i->Optional.of(new CuentaSnapshot(i.getArgument(0),"test@example.test","ACTIVO",2,3)));
        when(filas.buscarBloqueo(any())).thenReturn(Optional.empty());
        when(filas.bloquearBloqueo(any())).thenReturn(Optional.empty());
        when(filas.bloquearRacha(any())).thenReturn(Optional.empty());
        when(huellas.claveActualId()).thenReturn("test");
    }
    String ejecutar(boolean bloquear,String motivo){return servicio.ejecutar(actor,id.toString(),bloquear,motivo,contexto);}
    @Test void sinPermisoNoValidaNiConsultaExistenciaYAudita() {
        when(operaciones.autorizado(actor)).thenReturn(false);
        assertThat(servicio.ejecutar(actor,"id-malo",true,null,contexto)).isEqualTo("SCOPE_INSUFICIENTE");
        verifyNoInteractions(cuentas,filas,tokens);verify(operaciones).denegado(actor,contexto,ahora);
    }
    @Test void motivoObligatorioYMaximoSinEfectos() {
        for(String motivo:Arrays.asList(null,"","  ","x".repeat(501)))assertThat(ejecutar(true,motivo)).isEqualTo("VALIDACION");
        verifyNoInteractions(cuentas,filas,tokens);
    }
    @Test void inexistenteNoEscribe() {
        when(cuentas.leer(id)).thenReturn(Optional.empty());
        assertThat(ejecutar(true,"motivo")).isEqualTo("NO_ENCONTRADO");verifyNoInteractions(filas,tokens);
    }
    @Test void bloqueaSinVencimientoRevocaAntesDePersistirYNotifica() {
        assertThat(ejecutar(true," motivo ")).isNull();
        var orden=inOrder(cuentas,operaciones,filas,tokens);
        orden.verify(cuentas).bloquearCatalogoAdministrador();
        orden.verify(operaciones).revocarSesiones(id);
        orden.verify(filas).bloquearBloqueo(id);
        orden.verify(filas).guardarBloqueo(argThat(b->b.tipo()==TipoBloqueo.MANUAL && b.bloqueadoHasta()==null && b.motivo().equals("motivo")));
        orden.verify(tokens).invalidarPrevios(id);
        orden.verify(operaciones).registrar(eq(true),any(),eq(actor),eq(contexto),eq(ahora));
    }
    @Test void protegeAdministradorPropioYUltimo() {
        assertThat(servicio.ejecutar(actor,actor.sub().toString(),true,"motivo",contexto)).isEqualTo("ADMINISTRADOR_PROTEGIDO");
        when(operaciones.esAdministrador(id)).thenReturn(true);
        assertThat(ejecutar(true,"motivo")).isEqualTo("ADMINISTRADOR_PROTEGIDO");
        verify(operaciones,never()).revocarSesiones(any());verifyNoInteractions(filas,tokens);
    }
    @Test void cuentaNoOperativaNoSeBloquea() {
        for(String estado:List.of("INACTIVO","PENDIENTE_VERIFICACION")) {
            when(cuentas.leer(id)).thenReturn(Optional.of(new CuentaSnapshot(id,"test@example.test",estado,0,0)));
            assertThat(ejecutar(true,"motivo")).isEqualTo("CUENTA_NO_DISPONIBLE");
        }
        verifyNoInteractions(filas,tokens);
    }
    @Test void desbloquearActivaOVencidaNoTocaContadoresNiEfectos() {
        assertThat(ejecutar(false,null)).isNull();
        when(filas.buscarBloqueo(id)).thenReturn(Optional.of(new Bloqueo(id,TipoBloqueo.AUTOMATICO,ahora,null,ahora.minusSeconds(60),UUID.randomUUID())));
        assertThat(ejecutar(false,null)).isNull();verify(cuentas,never()).guardarContadores(any(),anyInt(),anyInt());
        verify(operaciones,never()).registrar(anyBoolean(),any(),any(),any(),any());
    }
    @Test void desbloqueaManualYReiniciaRachaSinRevocarMasSesiones() {
        var b=new Bloqueo(id,TipoBloqueo.MANUAL,null,"motivo",ahora,UUID.randomUUID());
        when(filas.buscarBloqueo(id)).thenReturn(Optional.of(b));when(filas.bloquearBloqueo(id)).thenReturn(Optional.of(b));
        when(filas.eliminarBloqueo(id,b.revision())).thenReturn(true);
        assertThat(ejecutar(false,null)).isNull();verify(cuentas).guardarContadores(id,2,0);
        verify(filas).crearRacha(any());verify(tokens).invalidarPrevios(id);verify(operaciones,never()).revocarSesiones(any());
    }
}
