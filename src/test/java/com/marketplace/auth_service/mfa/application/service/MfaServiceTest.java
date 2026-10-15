package com.marketplace.auth_service.mfa.application.service;

import com.marketplace.auth_service.mfa.application.dto.*;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static com.marketplace.auth_service.shared.domain.Rol.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MfaServiceTest {
    private static final UUID USUARIO=UUID.randomUUID(), CREDENCIAL=UUID.randomUUID(), ID=UUID.randomUUID();
    private static final Instant AHORA=Instant.parse("2026-10-14T05:00:00Z");
    private static final String TOKEN="A".repeat(43), HASH="a".repeat(64);
    private final CuentasMfaPort cuentas=mock(CuentasMfaPort.class);
    private final RolesMfaPort roles=mock(RolesMfaPort.class);
    private final RevisionCredencialMfaPort credenciales=mock(RevisionCredencialMfaPort.class);
    private final AlmacenDesafiosMfaPort almacen=mock(AlmacenDesafiosMfaPort.class);
    private final CriptografiaDesafiosMfaPort crypto=mock(CriptografiaDesafiosMfaPort.class);
    private final RevisionCredencialMfa revision=new RevisionCredencialMfa(USUARIO,CREDENCIAL,7);
    private final MfaService servicio=new MfaService(cuentas,roles,credenciales,almacen,crypto,
            Clock.fixed(AHORA,ZoneOffset.UTC),() -> ID);

    @BeforeEach void preparar() {
        cuenta(true,true,false);
        when(roles.leer(USUARIO)).thenReturn(Set.of(ADMIN_SISTEMA));
        when(credenciales.confirmar(revision)).thenReturn(true);
        when(crypto.generarDesafio()).thenReturn(new DesafioGenerado(TOKEN,HASH));
    }
    private void cuenta(boolean disponible,boolean verificado,boolean voluntario) {
        when(cuentas.leer(USUARIO)).thenReturn(Optional.of(new CuentaMfaSnapshot(USUARIO,disponible,verificado,voluntario)));
    }
    @Test void creaSoloDesafioPor300SegundosConRevisionYHash() {
        var resultado=(ResultadoCreacionDesafio.Creado)servicio.crearDesafio(USUARIO,revision);
        assertThat(resultado.desafio().mfaRequerido()).isTrue();
        assertThat(resultado.desafio().challengeToken()).isEqualTo(TOKEN);
        assertThat(resultado.desafio().canal()).isEqualTo(CanalMfa.EMAIL);
        assertThat(resultado.desafio().expiraEn()).isEqualTo(300);
        var captor=ArgumentCaptor.forClass(DesafioMfa.class);
        verify(almacen).guardarDesafio(captor.capture());
        var modelo=captor.getValue();
        assertThat(modelo.id()).isEqualTo(ID);
        assertThat(modelo.correspondeA(USUARIO,CREDENCIAL,7)).isTrue();
        assertThat(modelo.hashToken()).isEqualTo(HASH);
        assertThat(modelo.expiraEn()).isEqualTo(AHORA.plusSeconds(300));
        assertThat(modelo.consumido()).isFalse();
        verify(crypto).generarDesafio();
        verifyNoMoreInteractions(crypto,almacen);
    }
    @Test void clienteSinMfaNoGeneraNiPersisteDesafio() {
        when(roles.leer(USUARIO)).thenReturn(Set.of(CLIENTE));
        assertThat(servicio.crearDesafio(USUARIO,revision)).isEqualTo(
                new ResultadoCreacionDesafio.NoRequerido(new EstadoMfa(false,false)));
        verifyNoInteractions(crypto,almacen);
    }
    @Test void clienteConBanderaVoluntariaSiCreaDesafioEmail() {
        cuenta(true,true,true); when(roles.leer(USUARIO)).thenReturn(Set.of(CLIENTE));
        assertThat(servicio.crearDesafio(USUARIO,revision)).isInstanceOf(ResultadoCreacionDesafio.Creado.class);
    }
    @Test void cuentaInexistenteRespondeRechazoGenericoSinEfectos() {
        when(cuentas.leer(USUARIO)).thenReturn(Optional.empty());
        assertThat(servicio.crearDesafio(USUARIO,revision)).isEqualTo(ResultadoCreacionDesafio.Rechazado.INVALIDO);
        verifyNoInteractions(credenciales,roles,crypto,almacen);
    }
    @Test void cuentaNoDisponibleONoVerificadaNoCreaDesafio() {
        cuenta(false,true,true);
        assertThat(servicio.crearDesafio(USUARIO,revision)).isEqualTo(ResultadoCreacionDesafio.Rechazado.INVALIDO);
        cuenta(true,false,true);
        assertThat(servicio.crearDesafio(USUARIO,revision)).isEqualTo(ResultadoCreacionDesafio.Rechazado.INVALIDO);
        verifyNoInteractions(crypto,almacen,credenciales);
    }
    @Test void revisionDeOtraPersonaSeRechazaAntesDeConsultarCuentas() {
        assertThat(servicio.crearDesafio(USUARIO,new RevisionCredencialMfa(UUID.randomUUID(),CREDENCIAL,7)))
                .isEqualTo(ResultadoCreacionDesafio.Rechazado.INVALIDO);
        verifyNoInteractions(cuentas,credenciales,roles,crypto,almacen);
    }
    @Test void cuentaDelProveedorConOtroUuidNoSeUsa() {
        when(cuentas.leer(USUARIO)).thenReturn(Optional.of(new CuentaMfaSnapshot(UUID.randomUUID(),true,true,true)));
        assertThat(servicio.crearDesafio(USUARIO,revision)).isEqualTo(ResultadoCreacionDesafio.Rechazado.INVALIDO);
        verifyNoInteractions(credenciales,crypto,almacen);
    }
    @Test void revisionCambiadaNoGeneraTokenNiDecideLoginSinMfa() {
        when(credenciales.confirmar(revision)).thenReturn(false);
        when(roles.leer(USUARIO)).thenReturn(Set.of(CLIENTE));
        assertThat(servicio.crearDesafio(USUARIO,revision)).isEqualTo(ResultadoCreacionDesafio.Rechazado.INVALIDO);
        verifyNoInteractions(roles,crypto,almacen);
    }
    @Test void falloDePersistenciaPropagaSinResultadoDeExito() {
        doThrow(new IllegalStateException("fallo de prueba")).when(almacen).guardarDesafio(any());
        assertThatThrownBy(() -> servicio.crearDesafio(USUARIO,revision)).isInstanceOf(IllegalStateException.class);
    }
    @Test void lecturaDeEstadoProyectaGestionAunqueBanderaSeaFalse() {
        assertThat(servicio.leerEstado(USUARIO,Set.of(CLIENTE,GESTOR_DESPACHO))).contains(new EstadoMfa(true,true));
        assertThat(servicio.leerEstado(USUARIO,Set.of(CLIENTE))).contains(new EstadoMfa(false,false));
        verifyNoInteractions(crypto,almacen,credenciales);
    }
    @Test void lecturaDesconocidaDevuelveVacioSinInventarUsuario() {
        when(cuentas.leer(USUARIO)).thenReturn(Optional.empty());
        assertThat(servicio.leerEstado(USUARIO,Set.of(CLIENTE))).isEmpty();
    }
    @Test void resultadoNoMuestraTokenOHashEnToString() {
        var resultado=servicio.crearDesafio(USUARIO,revision);
        assertThat(resultado.toString()).doesNotContain(TOKEN,HASH);
    }
    @Test void dtoNoAceptaRevisionesNegativasOEstadosContradictorios() {
        assertThatThrownBy(() -> new RevisionCredencialMfa(USUARIO,CREDENCIAL,-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EstadoMfa(false,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultadoCreacionDesafio.NoRequerido(new EstadoMfa(true,false))).isInstanceOf(IllegalArgumentException.class);
    }
}
