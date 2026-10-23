package com.marketplace.auth_service.mfa.application.service;

import com.marketplace.auth_service.mfa.adapter.out.crypto.CriptografiaMfaJca;
import com.marketplace.auth_service.mfa.application.dto.*;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.mfa.domain.model.*;
import com.marketplace.auth_service.shared.domain.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CambioMfaServiceTest {
    static class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-10-20T17:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return ahora; }
    }
    Reloj reloj = new Reloj();
    UUID usuario = UUID.randomUUID(), familia = UUID.randomUUID();
    ActorUsuario actor = new ActorUsuario(usuario, UUID.randomUUID(), Set.of("CLIENTE"), Set.of());
    ContextoPeticion contexto = new ContextoPeticion("127.0.0.1", "test", UUID.randomUUID());
    CuentasMfaPort cuentas = mock(CuentasMfaPort.class);
    RolesMfaPort roles = mock(RolesMfaPort.class);
    PersistenciaMfaPort otp = mock(PersistenciaMfaPort.class);
    PersistenciaCambioMfaPort persistencia = mock(PersistenciaCambioMfaPort.class);
    EfectosCambioMfaPort efectos = mock(EfectosCambioMfaPort.class);
    CriptografiaMfaPort cripto = new CriptografiaMfaJca("test", Map.of("test", new byte[32]), new SecureRandom());
    Map<UUID,CambioMfa> cambios = new HashMap<>();
    Map<UUID,Otp> codigos = new HashMap<>();
    List<String> entregas = new ArrayList<>();
    boolean habilitado;
    CambioMfaService servicio;
    @BeforeEach void preparar() {
        when(cuentas.leer(usuario)).thenAnswer(i -> Optional.of(new CuentaMfaSnapshot(usuario,true,true,habilitado)));
        when(cuentas.contacto(usuario)).thenReturn(Optional.of(new ContactoMfa("a@example.test", "999123456")));
        when(roles.leer(usuario)).thenReturn(Set.of(Rol.CLIENTE));
        when(efectos.familia(actor)).thenReturn(Optional.of(familia));
        doAnswer(i -> { habilitado = i.getArgument(1); return null; }).when(efectos).guardarEstado(eq(usuario),anyBoolean());
        doAnswer(i -> { entregas.add(i.getArgument(1)); return null; }).when(efectos).enviarCodigo(anyString(),anyString());
        doAnswer(i -> { CambioMfa c=i.getArgument(0); cambios.put(c.id(),c); return null; }).when(persistencia).guardar(any());
        when(persistencia.buscarPorHash(anyString())).thenAnswer(i -> cambios.values().stream().filter(c -> c.hashToken().equals(i.getArgument(0))).findFirst());
        when(persistencia.bloquear(any())).thenAnswer(i -> Optional.ofNullable(cambios.get(i.getArgument(0))));
        doAnswer(i -> { Instant ahora=i.getArgument(1); cambios.replaceAll((id,c) -> !c.consumido() && c.invalidadoEn()==null
                ? new CambioMfa(c.id(),c.usuarioId(),c.familiaId(),c.habilitar(),c.hashToken(),c.creadoEn(),c.expiraEn(),false,ahora,c.otpActualId()) : c); return null;
        }).when(persistencia).invalidarPendientes(eq(usuario),any());
        doAnswer(i -> { Otp o=i.getArgument(0); codigos.put(o.contexto().otpId(),o); return null; }).when(otp).guardarOtp(any());
        when(otp.bloquearOtp(any())).thenAnswer(i -> Optional.ofNullable(codigos.get(i.getArgument(0))));
        when(otp.contarSolicitudes(eq(usuario),any())).thenAnswer(i -> codigos.values().stream().filter(o -> o.creadoEn().isAfter(i.getArgument(1))).count());
        when(otp.primeraSolicitudEnVentana(eq(usuario),any())).thenAnswer(i -> codigos.values().stream().map(Otp::creadoEn).filter(t -> t.isAfter(i.getArgument(1))).min(Comparator.naturalOrder()));
        servicio = new CambioMfaService(cuentas,roles,persistencia,otp,cripto,efectos,reloj);
    }
    ResultadoCambioMfa.Enviado solicitar(String accion) {
        return (ResultadoCambioMfa.Enviado) servicio.solicitar(actor,accion,contexto);
    }
    String ultimo() { return entregas.getLast(); }
    String errorDe(ResultadoCambioMfa r) { return ((ResultadoCambioMfa.Error)r).code(); }
    @Test void habilitaSoloTrasConfirmarYSinCrearSesion() {
        var e=solicitar("HABILITAR"); assertThat(habilitado).isFalse();
        assertThat(e.expiraEn()).isEqualTo(300); assertThat(e.cambioExpiraEn()).isEqualTo(600);
        assertThat(e.destinoEnmascarado()).doesNotContain("a@example.test");
        assertThat(servicio.confirmar(actor,e.mfaChangeToken(),ultimo(),contexto)).isEqualTo(ResultadoCambioMfa.Confirmado.EXITO);
        assertThat(habilitado).isTrue(); verify(efectos).auditar(eq("MFA_ACTIVADO"),eq(true),eq(true),eq(actor),eq(contexto),any());
    }
    @Test void deshabilitaVoluntario() {
        habilitado=true; var e=solicitar("DESHABILITAR");
        servicio.confirmar(actor,e.mfaChangeToken(),ultimo(),contexto); assertThat(habilitado).isFalse();
    }
    @Test void cualquierRolDeGestionImpideDesactivarAunqueBanderaFalse() {
        for(var rol:List.of(Rol.ADMIN_SISTEMA,Rol.ADMIN_VENTAS,Rol.GESTOR_COMERCIAL,Rol.GESTOR_DESPACHO)) {
            when(roles.leer(usuario)).thenReturn(Set.of(Rol.CLIENTE,rol));
            assertThat(errorDe(servicio.solicitar(actor,"DESHABILITAR",contexto))).isEqualTo("MFA_OBLIGATORIO");
        }
        assertThat(entregas).isEmpty();
    }
    @Test void estadoYaVigenteNoConsumeCuota() {
        assertThat(errorDe(servicio.solicitar(actor,"DESHABILITAR",contexto))).isEqualTo("VALIDACION");
        assertThat(codigos).isEmpty();
    }
    @Test void otroUsuarioOFamiliaNoConsumeNiComparaCodigo() {
        var e=solicitar("HABILITAR"); when(efectos.familia(actor)).thenReturn(Optional.of(UUID.randomUUID()));
        assertThat(errorDe(servicio.confirmar(actor,e.mfaChangeToken(),ultimo(),contexto))).isEqualTo("TOKEN_INVALIDO");
        assertThat(codigos.values().iterator().next().intentos()).isZero();
    }
    @Test void accesoRenovadoEnMismaFamiliaPuedeConfirmar() {
        var e=solicitar("HABILITAR"); var nuevo=new ActorUsuario(usuario,UUID.randomUUID(),Set.of(),Set.of());
        when(efectos.familia(nuevo)).thenReturn(Optional.of(familia));
        assertThat(servicio.confirmar(nuevo,e.mfaChangeToken(),ultimo(),contexto)).isEqualTo(ResultadoCambioMfa.Confirmado.EXITO);
    }
    @Test void familiaRevocadaRechazaAntesDeConsumir() {
        var e=solicitar("HABILITAR"); when(efectos.familia(actor)).thenReturn(Optional.empty());
        assertThat(errorDe(servicio.confirmar(actor,e.mfaChangeToken(),ultimo(),contexto))).isEqualTo("TOKEN_INVALIDO");
        assertThat(cambios.values().iterator().next().consumido()).isFalse();
    }
    @Test void tresFallosPersistenYAgotanCodigo() {
        var e=solicitar("HABILITAR"); String incorrecto=ultimo().equals("000000")?"000001":"000000";
        var primero=(ResultadoCambioMfa.Error)servicio.confirmar(actor,e.mfaChangeToken(),incorrecto,contexto);
        assertThat(primero.intentosRestantes()).isEqualTo(2);
        servicio.confirmar(actor,e.mfaChangeToken(),incorrecto,contexto);
        assertThat(errorDe(servicio.confirmar(actor,e.mfaChangeToken(),incorrecto,contexto))).isEqualTo("OTP_INTENTOS_AGOTADOS");
        assertThat(errorDe(servicio.confirmar(actor,e.mfaChangeToken(),ultimo(),contexto))).isEqualTo("OTP_INTENTOS_AGOTADOS");
        assertThat(habilitado).isFalse();
    }
    @Test void otpVenceEn300YCambioEn600Exactos() {
        var e=solicitar("HABILITAR"); reloj.ahora=reloj.ahora.plusSeconds(300);
        assertThat(errorDe(servicio.confirmar(actor,e.mfaChangeToken(),ultimo(),contexto))).isEqualTo("OTP_EXPIRADO");
        reloj.ahora=reloj.ahora.plusSeconds(300);
        assertThat(errorDe(servicio.confirmar(actor,e.mfaChangeToken(),ultimo(),contexto))).isEqualTo("TOKEN_INVALIDO");
    }
    @Test void reenvioSustituyeCodigoSinProrrogarCambioInclusoMismoInstante() {
        var e=solicitar("HABILITAR"); var anterior=codigos.keySet().iterator().next();
        var r=(ResultadoCambioMfa.Enviado)servicio.reenviar(actor,e.mfaChangeToken(),contexto);
        assertThat(codigos.get(anterior).invalidadoEn()).isNotNull();
        assertThat(cambios.values().iterator().next().otpActualId()).isNotEqualTo(anterior);
        reloj.ahora=reloj.ahora.plusSeconds(360);
        r=(ResultadoCambioMfa.Enviado)servicio.reenviar(actor,e.mfaChangeToken(),contexto);
        assertThat(r.cambioExpiraEn()).isEqualTo(240);
    }
    @Test void cuartaSolicitudIncluyendoLoginNoEnvia() {
        var e=solicitar("HABILITAR");
        var contextoLogin=new ContextoOtp(usuario,UUID.randomUUID(),UUID.randomUUID());
        var generado=cripto.generarOtp(contextoLogin);
        codigos.put(contextoLogin.otpId(),Otp.nuevo(contextoLogin,generado.hashCodigo(),generado.hashKeyId(),CanalOtp.EMAIL,"a@example.test",reloj.instant()));
        servicio.reenviar(actor,e.mfaChangeToken(),contexto);
        var r=(ResultadoCambioMfa.Error)servicio.reenviar(actor,e.mfaChangeToken(),contexto);
        assertThat(r.code()).isEqualTo("DEMASIADAS_SOLICITUDES"); assertThat(r.retryAfter()).isPositive(); assertThat(entregas).hasSize(2);
    }
    @Test void nuevaSolicitudInvalidaAnteriorYConfirmacionEsUnica() {
        var viejo=solicitar("HABILITAR"); var nuevo=solicitar("HABILITAR");
        assertThat(errorDe(servicio.confirmar(actor,viejo.mfaChangeToken(),ultimo(),contexto))).isEqualTo("TOKEN_INVALIDO");
        servicio.confirmar(actor,nuevo.mfaChangeToken(),ultimo(),contexto);
        assertThat(errorDe(servicio.confirmar(actor,nuevo.mfaChangeToken(),ultimo(),contexto))).isEqualTo("TOKEN_INVALIDO");
        verify(efectos,times(1)).guardarEstado(usuario,true);
    }
    @Test void tokenLoginNoEsOperacionDeCambioYPropositosHmacNoSeIntercambian() {
        assertThat(errorDe(servicio.confirmar(actor,cripto.generarDesafio().token(),"000000",contexto))).isEqualTo("TOKEN_INVALIDO");
        var id=UUID.randomUUID(); var c=new ContextoOtp(usuario,id,UUID.randomUUID()); var g=cripto.generarOtp(c);
        assertThat(cripto.coincideOtp(new ContextoOtp(usuario,id,c.otpId(),ContextoOtp.CAMBIO),g.codigo(),g.hashCodigo(),g.hashKeyId())).isFalse();
    }
    @Test void revalidaRolesAlConfirmar() {
        habilitado=true; var e=solicitar("DESHABILITAR"); when(roles.leer(usuario)).thenReturn(Set.of(Rol.ADMIN_SISTEMA));
        assertThat(errorDe(servicio.confirmar(actor,e.mfaChangeToken(),ultimo(),contexto))).isEqualTo("MFA_OBLIGATORIO"); assertThat(habilitado).isTrue();
    }
}
