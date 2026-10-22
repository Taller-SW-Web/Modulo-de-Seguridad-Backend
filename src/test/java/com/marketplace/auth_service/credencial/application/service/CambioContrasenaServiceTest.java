package com.marketplace.auth_service.credencial.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketplace.auth_service.credencial.application.dto.PreverificacionCredencial;
import com.marketplace.auth_service.credencial.application.dto.ResultadoCambioContrasena;
import com.marketplace.auth_service.credencial.application.dto.RevisionCredencial;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.dto.ValidacionPassword;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.out.EfectosCambioContrasenaPort;
import com.marketplace.auth_service.credencial.application.port.out.EscrituraCredencialPort;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.credencial.domain.model.AlgoritmoHash;
import com.marketplace.auth_service.credencial.domain.model.HashContrasena;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/** EP-03-T005 con dobles: orden de efectos, rechazos sin escribir y conservación de la sesión actual. */
class CambioContrasenaServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID JTI = UUID.randomUUID();
    private static final UUID FAMILIA = UUID.randomUUID();
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("10.0.0.1", "jdk", UUID.randomUUID());
    private static final RevisionCredencial REVISION = new RevisionCredencial(USUARIO, USUARIO, 2);

    private final CredencialesPort credenciales = mock(CredencialesPort.class);
    private final PasswordHasherPort hasher = mock(PasswordHasherPort.class);
    private final EscrituraCredencialPort escritura = mock(EscrituraCredencialPort.class);
    private final List<String> eventos = new ArrayList<>();
    private Optional<UUID> familia = Optional.of(FAMILIA);
    private CambioContrasenaService servicio;

    @BeforeEach
    void preparar() {
        when(hasher.generar(any())).thenReturn(new HashContrasena("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA",
                AlgoritmoHash.ARGON2ID));
        when(credenciales.validarNueva(any(), any())).thenReturn(new ValidacionPassword(List.of()));
        when(credenciales.preverificar(any(), any())).thenAnswer(i -> new PreverificacionCredencial(
                i.<SecretoEfimero>getArgument(1).valor().equals("Actual-2026!"), Optional.of(REVISION)));
        when(credenciales.confirmarRevision(REVISION)).thenReturn(true);
        EfectosCambioContrasenaPort efectos = new EfectosCambioContrasenaPort() {
            @Override public <T> T enTransaccion(Supplier<T> trabajo) { eventos.add("tx"); return trabajo.get(); }
            @Override public void bloquearCuenta(UUID u) { eventos.add("cuenta"); }
            @Override public Optional<UUID> familiaDe(UUID u, UUID jti) { return familia; }
            @Override public void revocarOtras(UUID u, UUID f) { eventos.add("revocarOtras:" + f); }
            @Override public void revocarTodas(UUID u) { eventos.add("revocarTodas"); }
            @Override public void auditarCambio(UUID u, ContextoPeticion c) { eventos.add("auditoria"); }
            @Override public void avisarCambio(UUID u) { eventos.add("aviso"); }
        };
        servicio = new CambioContrasenaService(credenciales, hasher, escritura, efectos,
                Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneOffset.UTC));
    }

    private ResultadoCambioContrasena cambiar(String actual, String nueva) {
        return servicio.cambiar(USUARIO, JTI, new SecretoEfimero(actual), new SecretoEfimero(nueva), CONTEXTO);
    }

    @Test
    void cambiaEnOrdenYConservaLaSesionQueHizoElCambio() {
        assertThat(cambiar("Actual-2026!", "Nueva-Segura-2026!")).isEqualTo(ResultadoCambioContrasena.Simple.CAMBIADA);
        verify(escritura).reemplazar(ArgumentMatchers.eq(USUARIO), anyString(), any());
        assertThat(eventos).containsExactly("tx", "cuenta", "revocarOtras:" + FAMILIA, "auditoria", "aviso");
    }

    @Test
    void actualIncorrectaNoAbreTransaccionNiEscribe() {
        assertThat(cambiar("mala", "Nueva-Segura-2026!")).isEqualTo(ResultadoCambioContrasena.Simple.ACTUAL_INCORRECTA);
        verify(escritura, never()).reemplazar(any(), any(), any());
        assertThat(eventos).isEmpty();
    }

    @Test
    void politicaIncumplidaDevuelveSusReglasSinEscribir() {
        var invalida = mock(ValidacionPassword.class);
        when(invalida.valida()).thenReturn(false);
        when(credenciales.validarNueva(any(), any())).thenReturn(invalida);
        assertThat(cambiar("Actual-2026!", "corta")).isInstanceOf(ResultadoCambioContrasena.PoliticaIncumplida.class);
        verify(escritura, never()).reemplazar(any(), any(), any());
    }

    @Test
    void revisionCambiadaEntretantoNoEscribe() {
        when(credenciales.confirmarRevision(REVISION)).thenReturn(false);
        assertThat(cambiar("Actual-2026!", "Nueva-Segura-2026!")).isEqualTo(ResultadoCambioContrasena.Simple.ACTUAL_INCORRECTA);
        verify(escritura, never()).reemplazar(any(), any(), any());
        assertThat(eventos).containsExactly("tx", "cuenta");
    }

    @Test
    void sinFamiliaVigenteNoCambiaNiRevoca() {
        familia = Optional.empty();
        assertThat(cambiar("Actual-2026!", "Nueva-Segura-2026!"))
                .isEqualTo(ResultadoCambioContrasena.Simple.SESION_INVALIDA);
        verify(escritura, never()).reemplazar(any(), any(), any());
        assertThat(eventos).containsExactly("tx", "cuenta");
    }
}
