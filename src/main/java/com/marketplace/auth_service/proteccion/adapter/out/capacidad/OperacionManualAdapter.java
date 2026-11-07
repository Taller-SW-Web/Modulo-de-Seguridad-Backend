package com.marketplace.auth_service.proteccion.adapter.out.capacidad;

import com.marketplace.auth_service.proteccion.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.proteccion.application.port.out.OperacionManualPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.auditoria.application.port.in.*;
import com.marketplace.auth_service.auditoria.application.dto.*;
import com.marketplace.auth_service.shared.domain.*;
import java.time.Instant;
import java.util.*;

public final class OperacionManualAdapter implements OperacionManualPort {
    private final AutorizacionPort roles;
    private final CuentasPort cuentas;
    private final SesionesPort sesiones;
    private final AuditoriaPort auditoria;
    private final MensajeriaPort mensajes;
    public OperacionManualAdapter(AutorizacionPort roles, CuentasPort cuentas, SesionesPort sesiones,
            AuditoriaPort auditoria, MensajeriaPort mensajes) {
        this.roles=roles;this.cuentas=cuentas;this.sesiones=sesiones;this.auditoria=auditoria;this.mensajes=mensajes;
    }
    @Override public boolean autorizado(ActorUsuario actor) {
        var actual=roles.resolver(actor.sub());
        return actual.roles().contains(Rol.ADMIN_SISTEMA) && actual.permisos().contains(Permiso.CUENTA_BLOQUEAR);
    }
    @Override public boolean esAdministrador(UUID id) { return roles.resolver(id).roles().contains(Rol.ADMIN_SISTEMA); }
    @Override public long otrosAdministradoresActivos(UUID id) {
        var otros=new HashSet<>(roles.usuariosConRol(Rol.ADMIN_SISTEMA)); otros.remove(id);
        return otros.isEmpty() ? 0 : cuentas.contarActivos(otros);
    }
    @Override public void revocarSesiones(UUID id) { sesiones.revocarTodas(id); }
    @Override public void registrar(boolean bloquear, CuentaSnapshot cuenta, ActorUsuario actor, ContextoPeticion contexto, Instant fecha) {
        String accion=bloquear ? "CUENTA_BLOQUEADA" : "CUENTA_DESBLOQUEADA";
        auditoria.registrarCritica(new RegistroSeguro(UUID.randomUUID(),accion,"EXITO","USUARIO",actor.sub().toString(),
                cuenta.usuarioId(),fecha,contexto.ip(),contexto.agente(),Map.of("via","ADMINISTRADOR")));
        var carga=new HashMap<String,String>();
        carga.put("usuarioId",cuenta.usuarioId().toString()); carga.put("fecha",fecha.toString());
        if(bloquear) carga.put("automatico","false"); else carga.put("via","ADMINISTRADOR");
        mensajes.guardarEvento(new EventoUsuario(UUID.randomUUID(),bloquear ? "usuario.bloqueado" : "usuario.desbloqueado",carga));
        mensajes.guardarCorreo(new EntregaCorreo(UUID.randomUUID(),bloquear ? "correo.cuenta_bloqueada" : "correo.cuenta_desbloqueada",
                cuenta.correo(),bloquear ? "Un administrador bloqueó tu cuenta. Solicita al administrador de la plataforma que revise tu cuenta."
                        : "Un administrador desbloqueó tu cuenta. Ya puedes iniciar sesión."));
    }
    @Override public void denegado(ActorUsuario actor, ContextoPeticion contexto, Instant fecha) {
        try {
        auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(),"ACCESO_DENEGADO","FALLO","USUARIO",
                actor.sub().toString(),null,fecha,contexto.ip(),contexto.agente(),Map.of("via","ADMINISTRADOR")));
        } catch (RuntimeException noCritica) {
            // La indisponibilidad de la auditoría no concede acceso ni cambia el 403.
        }
    }
}
