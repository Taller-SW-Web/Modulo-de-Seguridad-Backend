package com.marketplace.auth_service.mfa.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.mfa.application.port.out.EfectosCambioMfaPort;
import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentaEscrituraInterna;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

public final class EfectosCambioMfaAdapter implements EfectosCambioMfaPort {
    private final SesionesPort sesiones;
    private final CuentaEscrituraInterna cuentas;
    private final MensajeriaPort mensajes;
    private final AuditoriaPort auditoria;
    public EfectosCambioMfaAdapter(SesionesPort sesiones, CuentaEscrituraInterna cuentas,
            MensajeriaPort mensajes, AuditoriaPort auditoria) {
        this.sesiones = sesiones; this.cuentas = cuentas; this.mensajes = mensajes; this.auditoria = auditoria;
    }
    @Override public Optional<UUID> familia(ActorUsuario actor) {
        try { return Optional.of(sesiones.resolverAcceso(new AccesoVerificado(actor.sub(), actor.jti())).familiaId()); }
        catch (DataAccessException | TransactionException tecnico) { throw tecnico; }
        catch (RuntimeException accesoDesconocidoORevocado) { return Optional.empty(); }
    }
    @Override public void guardarEstado(UUID usuario, boolean habilitado) { cuentas.guardarMfaVoluntario(usuario, habilitado); }
    @Override public void enviarCodigo(String correo, String codigo) {
        mensajes.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "cambio_mfa_otp", correo,
                "Tu código para cambiar el segundo factor es " + codigo
                + ". Válido durante 5 minutos y mientras el cambio siga vigente. Usa el último código solicitado."));
    }
    @Override public void avisarCambio(String correo, boolean habilitado) {
        mensajes.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "cambio_mfa_confirmado", correo,
                "Se confirmó la " + (habilitado ? "activación" : "desactivación")
                + " de tu segundo factor. Si no realizaste este cambio, contacta con el administrador de la plataforma."));
    }
    @Override public void auditar(String accion, boolean exito, boolean critica, ActorUsuario actor,
            ContextoPeticion contexto, Instant fecha) {
        var registro = new RegistroSeguro(UUID.randomUUID(), accion, exito ? "EXITO" : "FALLO", "USUARIO",
                actor.sub().toString(), actor.sub(), fecha, contexto.ip(), contexto.agente(), Map.of("proposito", "CAMBIO_MFA"));
        if (critica) auditoria.registrarCritica(registro); else auditoria.registrarNoCriticaTrasCommit(registro);
    }
}
