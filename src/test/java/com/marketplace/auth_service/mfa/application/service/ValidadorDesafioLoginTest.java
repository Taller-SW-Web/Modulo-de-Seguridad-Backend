package com.marketplace.auth_service.mfa.application.service;

import com.marketplace.auth_service.mfa.adapter.out.crypto.CriptografiaMfaJca;
import com.marketplace.auth_service.mfa.application.dto.*;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidadorDesafioLoginTest {
    private static final UUID USUARIO=UUID.randomUUID(), CREDENCIAL=UUID.randomUUID(), ID=UUID.randomUUID();
    private static final Instant AHORA=Instant.parse("2026-10-14T05:00:00Z");
    private static final String TOKEN="A".repeat(43);
    private final CriptografiaDesafiosMfaPort crypto=new CriptografiaMfaJca("test",Map.of("test",new byte[32]),new SecureRandom());
    private final String hash=crypto.hashDesafio(TOKEN);
    private final AlmacenDesafiosMfaPort almacen=mock(AlmacenDesafiosMfaPort.class);
    private final CuentasMfaPort cuentas=mock(CuentasMfaPort.class);
    private final RolesMfaPort roles=mock(RolesMfaPort.class);
    private final RevisionCredencialMfaPort credenciales=mock(RevisionCredencialMfaPort.class);
    private final Clock reloj=mock(Clock.class);
    private final ValidadorDesafioLogin validador=new ValidadorDesafioLogin(almacen,crypto,cuentas,roles,credenciales,reloj);
    private DesafioMfa modelo;

    @BeforeEach void preparar() {
        modelo=DesafioMfa.nuevo(ID,USUARIO,CREDENCIAL,7,hash,AHORA);
        when(almacen.buscarDesafioPorHash(anyString())).thenAnswer(inv ->
                hash.equals(inv.getArgument(0)) ? Optional.ofNullable(modelo) : Optional.empty());
        when(reloj.instant()).thenReturn(AHORA.plusSeconds(1));
        when(cuentas.leer(USUARIO)).thenReturn(Optional.of(new CuentaMfaSnapshot(USUARIO,true,true,false)));
        when(roles.leer(USUARIO)).thenReturn(Set.of("GESTOR_COMERCIAL"));
        when(credenciales.confirmar(any())).thenReturn(true);
    }
    @Test void localizaConHashRealYConfirmaRevisionSinConsumir() {
        assertThat(validador.localizarVigente(TOKEN)).contains(modelo);
        assertThat(validador.localizarVigente(TOKEN)).contains(modelo);
        verify(credenciales,times(2)).confirmar(new RevisionCredencialMfa(USUARIO,CREDENCIAL,7));
        verify(almacen,never()).guardarDesafio(any());
        assertThat(modelo.consumido()).isFalse();
    }
    @Test void tokenManipuladoCanonicoNoEncuentraElDesafio() {
        assertThat(validador.localizarVigente("B"+TOKEN.substring(1))).isEmpty();
        verifyNoInteractions(cuentas,roles,credenciales);
    }
    @Test void formatoInvalidoOVacioNoConsultaBaseNiRevelaCuenta() {
        for(String t:java.util.Arrays.asList(null,"","jwt.payload.signature","chg_inventado","A".repeat(42))) {
            assertThat(validador.localizarVigente(t)).isEmpty();
        }
        verifyNoInteractions(almacen,cuentas,roles,credenciales);
    }
    @Test void vencimientoExactoYCambioDelRelojNoExtiendenDesafio() {
        when(reloj.instant()).thenReturn(AHORA.plusSeconds(300));
        assertThat(validador.localizarVigente(TOKEN)).isEmpty();
        when(reloj.instant()).thenReturn(AHORA.minusSeconds(1));
        assertThat(validador.localizarVigente(TOKEN)).isEmpty();
        verifyNoInteractions(cuentas,credenciales);
    }
    @Test void historicoSinRevisionNoEsUnaPruebaDeLogin() {
        modelo=new DesafioMfa(ID,USUARIO,null,null,hash,AHORA,AHORA.plusSeconds(300),false);
        assertThat(validador.localizarVigente(TOKEN)).isEmpty();
        verifyNoInteractions(credenciales);
    }
    @Test void desafioConsumidoNoSeLocalizaComoVigente() {
        modelo=new DesafioMfa(ID,USUARIO,CREDENCIAL,7L,hash,AHORA,AHORA.plusSeconds(300),true);
        assertThat(validador.localizarVigente(TOKEN)).isEmpty();
    }
    @Test void cambioDeCredencialInvalidaDesafioSinModificarlo() {
        when(credenciales.confirmar(any())).thenReturn(false);
        assertThat(validador.localizarVigente(TOKEN)).isEmpty();
        verify(almacen,never()).guardarDesafio(any());
    }
    @Test void cuentaBloqueadaODesaparecidaNoContinua() {
        when(cuentas.leer(USUARIO)).thenReturn(Optional.of(new CuentaMfaSnapshot(USUARIO,false,true,true)));
        assertThat(validador.localizarVigente(TOKEN)).isEmpty();
        when(cuentas.leer(USUARIO)).thenReturn(Optional.empty());
        assertThat(validador.localizarVigente(TOKEN)).isEmpty();
        verifyNoInteractions(credenciales);
    }
    @Test void mfaDeClienteDesactivadoObligaANuevoLogin() {
        when(roles.leer(USUARIO)).thenReturn(Set.of("CLIENTE"));
        assertThat(validador.localizarVigente(TOKEN)).isEmpty();
    }
    @Test void cuentaDeOtroUsuarioNoSustituyeAlDuenoDelDesafio() {
        when(cuentas.leer(USUARIO)).thenReturn(Optional.of(new CuentaMfaSnapshot(UUID.randomUUID(),true,true,true)));
        assertThat(validador.localizarVigente(TOKEN)).isEmpty();
    }
}
