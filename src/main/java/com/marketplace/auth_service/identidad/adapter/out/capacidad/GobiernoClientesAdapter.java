package com.marketplace.auth_service.identidad.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.identidad.application.port.out.GobiernoClientesPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Puente de la gestión de clientes técnicos con autorización y auditoría
 * (SPEC-19). El permiso se lee de la base, no de los claims del token.
 */
public final class GobiernoClientesAdapter implements GobiernoClientesPort {

    private final AutorizacionPort autorizacion;
    private final AuditoriaPort auditoria;

    public GobiernoClientesAdapter(AutorizacionPort autorizacion, AuditoriaPort auditoria) {
        this.autorizacion = Objects.requireNonNull(autorizacion, "autorizacion");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
    }

    @Override
    public boolean autorizado(ActorUsuario actor) {
        var actuales = autorizacion.resolver(actor.sub());
        return actuales.tiene(Rol.ADMIN_SISTEMA) && actuales.tiene(Permiso.INTEGRACION_GESTIONAR);
    }

    @Override
    public void registrar(String accion, ActorUsuario actor, ContextoPeticion contexto, Instant fecha,
                          Map<String, String> detalle) {
        auditoria.registrarCritica(new RegistroSeguro(UUID.randomUUID(), accion, "EXITO", "USUARIO",
                actor.sub().toString(), null, fecha, ip(contexto), agente(contexto), detalle));
    }

    @Override
    public void denegado(ActorUsuario actor, ContextoPeticion contexto, Instant fecha) {
        try {
            auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "ACCESO_DENEGADO",
                    "FALLO", "USUARIO", actor.sub().toString(), null, fecha, ip(contexto), agente(contexto),
                    Map.of("via", "CLIENTES_TECNICOS")));
        } catch (RuntimeException noCritica) {
            // La indisponibilidad de la auditoría no concede acceso ni cambia el 403.
        }
    }

    private static String ip(ContextoPeticion contexto) {
        return contexto == null ? null : contexto.ip();
    }

    private static String agente(ContextoPeticion contexto) {
        return contexto == null ? null : contexto.agente();
    }
}
