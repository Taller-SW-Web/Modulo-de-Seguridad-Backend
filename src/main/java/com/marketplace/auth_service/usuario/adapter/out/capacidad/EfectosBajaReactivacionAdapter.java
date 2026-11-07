package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.port.out.EfectosBajaReactivacionPort;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Puente de la baja y la reactivación hacia autorización (Sergio), sesiones y auditoría (Christian). */
public final class EfectosBajaReactivacionAdapter implements EfectosBajaReactivacionPort {

    private final AutorizacionPort autorizacion;
    private final SesionesPort sesiones;
    private final AuditoriaPort auditoria;
    private final MensajeriaPort mensajeria;

    public EfectosBajaReactivacionAdapter(AutorizacionPort autorizacion, SesionesPort sesiones,
                                          AuditoriaPort auditoria, MensajeriaPort mensajeria) {
        this.autorizacion = autorizacion;
        this.sesiones = sesiones;
        this.auditoria = auditoria;
        this.mensajeria = mensajeria;
    }

    @Override
    public boolean autorizado(UUID actor, Permiso permiso) {
        var actual = autorizacion.resolver(actor);
        return actual.roles().contains(Rol.ADMIN_SISTEMA) && actual.permisos().contains(permiso);
    }

    @Override
    public void bloquearCatalogoAdministrador() { autorizacion.bloquearCatalogoAdministrador(); }

    @Override
    public boolean esAdministrador(UUID usuarioId) {
        return autorizacion.resolver(usuarioId).roles().contains(Rol.ADMIN_SISTEMA);
    }

    @Override
    public Set<UUID> administradores() { return autorizacion.usuariosConRol(Rol.ADMIN_SISTEMA); }

    @Override
    public void revocarSesiones(UUID usuarioId) { sesiones.revocarTodas(usuarioId); }

    @Override
    public void registrarBaja(UUID usuarioId, ActorUsuario actor, ContextoPeticion contexto, Instant ahora) {
        auditoria.registrarCritica(registro("USUARIO_DESACTIVADO", usuarioId, actor, contexto, ahora));
        // Catálogo: "datos": {"motivo": "BAJA_ADMINISTRATIVA"}; el sobre copia las claves de texto.
        mensajeria.guardarEvento(new EventoUsuario(UUID.randomUUID(), "usuario.desactivado", Map.of(
                "usuarioId", usuarioId.toString(), "fecha", ahora.toString(), "motivo", "BAJA_ADMINISTRATIVA")));
    }

    @Override
    public void registrarReactivacion(UUID usuarioId, ActorUsuario actor, ContextoPeticion contexto, Instant ahora) {
        auditoria.registrarCritica(registro("USUARIO_REACTIVADO", usuarioId, actor, contexto, ahora));
        // Catálogo: "datos": {"roles": [...]}; MensajeriaService.sobre() arma la lista desde la carga.
        mensajeria.guardarEvento(new EventoUsuario(UUID.randomUUID(), "usuario.reactivado", Map.of(
                "usuarioId", usuarioId.toString(), "fecha", ahora.toString(),
                "roles", String.join(",", autorizacion.resolver(usuarioId).codigosRoles()))));
    }

    @Override
    public void denegado(ActorUsuario actor, ContextoPeticion contexto, Instant ahora) {
        try {
            auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "ACCESO_DENEGADO", "FALLO",
                    "USUARIO", actor.sub().toString(), null, ahora, contexto.ip(), contexto.agente(),
                    Map.of("via", "ADMINISTRADOR")));
        } catch (RuntimeException noCritica) {
            // La indisponibilidad de la auditoría no concede acceso ni cambia el 403.
        }
    }

    private static RegistroSeguro registro(String accion, UUID usuarioId, ActorUsuario actor, ContextoPeticion contexto,
                                           Instant ahora) {
        return new RegistroSeguro(UUID.randomUUID(), accion, "EXITO", "USUARIO", actor.sub().toString(), usuarioId,
                ahora, contexto.ip(), contexto.agente(), Map.of("via", "ADMINISTRADOR"));
    }
}
