package com.marketplace.auth_service.credencial.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.credencial.application.port.out.EfectosRestablecimientoPort;
import com.marketplace.auth_service.credencial.application.port.out.EscrituraCredencialPort;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.credencial.domain.model.AlgoritmoHash;
import com.marketplace.auth_service.credencial.domain.model.HashContrasena;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestablecerContrasenaServiceTest {
    private static final UUID USUARIO=UUID.randomUUID();
    private static final UUID TOKEN_ID=UUID.randomUUID();
    private static final Instant AHORA=Instant.parse("2026-10-22T10:00:00Z");
    private static final ContextoPeticion CONTEXTO=new ContextoPeticion("127.0.0.1","test",UUID.randomUUID());
    private final TokensUnUsoPort tokens=mock(TokensUnUsoPort.class);
    private final CredencialesPort credenciales=mock(CredencialesPort.class);
    private final PasswordHasherPort hasher=mock(PasswordHasherPort.class);
    private final EscrituraCredencialPort escritura=mock(EscrituraCredencialPort.class);
    private final EfectosRestablecimientoPort efectos=mock(EfectosRestablecimientoPort.class);
    private final String claro="token-seguro-no-registrado";
    private final String digest=TokenUnUsoHashing.sha256Hex(claro);
    private RestablecerContrasenaService servicio;

    @BeforeEach void preparar() {
        var token=new TokenUnUsoSnapshot(TOKEN_ID,USUARIO,PropositoToken.RECUPERACION,null,null,
                AHORA.plusSeconds(300),AHORA.minusSeconds(60),false);
        when(tokens.localizar(digest,PropositoToken.RECUPERACION)).thenReturn(Optional.of(token));
        when(tokens.consumir(TOKEN_ID,USUARIO,PropositoToken.RECUPERACION,AHORA)).thenReturn(true);
        when(credenciales.validarNueva(eq(USUARIO),any())).thenReturn(new ValidacionPassword(List.of()));
        when(hasher.generar(any())).thenReturn(new HashContrasena("$argon2id$hash",AlgoritmoHash.ARGON2ID));
        when(efectos.enTransaccion(any())).thenAnswer(i -> ((Supplier<?>)i.getArgument(0)).get());
        servicio=new RestablecerContrasenaService(tokens,credenciales,hasher,escritura,efectos,
                Clock.fixed(AHORA,ZoneOffset.UTC));
    }

    @Test void tokenVigenteValidaPoliticaConsumeCambiaYAplicaEfectos() {
        var resultado=servicio.restablecer(new SecretoEfimero(claro),new SecretoEfimero("Nueva-Segura-2026!"),CONTEXTO);
        assertThat(resultado).isEqualTo(ResultadoRestablecimiento.Simple.RESTABLECIDA);
        verify(tokens).consumir(TOKEN_ID,USUARIO,PropositoToken.RECUPERACION,AHORA);
        verify(escritura).reemplazar(eq(USUARIO),eq("$argon2id$hash"),eq(AHORA));
        verify(efectos).bloquearCuenta(USUARIO);
        verify(efectos).prepararProteccion(USUARIO);
        verify(efectos).restablecerProteccion(USUARIO,CONTEXTO,AHORA);
        verify(efectos).revocarSesiones(USUARIO);
        verify(efectos).auditar(USUARIO,CONTEXTO,AHORA);
        verify(efectos).avisar(USUARIO);
    }

    @Test void politicaRechazadaNoConsumeTokenYPermiteReintentarConUnaContrasenaValida() {
        var invalida=new ValidacionPassword(List.of(new InfraccionPassword(ReglaPassword.LONGITUD_MINIMA,10)));
        when(credenciales.validarNueva(eq(USUARIO),any())).thenReturn(invalida,new ValidacionPassword(List.of()));
        assertThat(servicio.restablecer(new SecretoEfimero(claro),new SecretoEfimero("corta"),CONTEXTO))
                .isInstanceOf(ResultadoRestablecimiento.PoliticaIncumplida.class);
        verify(tokens,never()).consumir(any(),any(),any(),any(Instant.class));
        verify(escritura,never()).reemplazar(any(),any(),any());
        verify(efectos,never()).revocarSesiones(any());
        assertThat(servicio.restablecer(new SecretoEfimero(claro),new SecretoEfimero("Nueva-Segura-2026!"),CONTEXTO))
                .isEqualTo(ResultadoRestablecimiento.Simple.RESTABLECIDA);
        verify(tokens).consumir(TOKEN_ID,USUARIO,PropositoToken.RECUPERACION,AHORA);
    }

    @Test void tokenVencidoSeRechazaSinAbrirTransaccion() {
        var vencido=new TokenUnUsoSnapshot(TOKEN_ID,USUARIO,PropositoToken.RECUPERACION,null,null,
                AHORA,AHORA.minusSeconds(3600),false);
        when(tokens.localizar(digest,PropositoToken.RECUPERACION)).thenReturn(Optional.of(vencido));
        assertThat(servicio.restablecer(new SecretoEfimero(claro),new SecretoEfimero("Nueva-Segura-2026!"),CONTEXTO))
                .isEqualTo(ResultadoRestablecimiento.Simple.TOKEN_EXPIRADO);
        verify(efectos,never()).enTransaccion(any());
    }

    @Test void tokenUsadoOInexistenteNoSeAcepta() {
        when(tokens.localizar(digest,PropositoToken.RECUPERACION)).thenReturn(Optional.empty());
        assertThat(servicio.restablecer(new SecretoEfimero(claro),new SecretoEfimero("Nueva-Segura-2026!"),CONTEXTO))
                .isEqualTo(ResultadoRestablecimiento.Simple.TOKEN_INVALIDO);
        verify(hasher,never()).generar(any());
        verify(efectos,never()).enTransaccion(any());
    }
}
