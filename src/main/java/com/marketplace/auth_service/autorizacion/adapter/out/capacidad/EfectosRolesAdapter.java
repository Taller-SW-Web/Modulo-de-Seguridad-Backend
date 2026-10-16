package com.marketplace.auth_service.autorizacion.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.autorizacion.application.dto.RegistroRol;
import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiadosEvento;
import com.marketplace.auth_service.autorizacion.application.port.out.AuditoriaRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.EventosRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.SesionesRolesPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Efectos de la gestión de roles hacia sus dueños: auditoría y outbox (Christian, EP-06 /
 * TEC-INFRA-T003) y revocación de sesiones (EP-02). Solo traduce DTO; no decide reglas.
 */
public final class EfectosRolesAdapter implements AuditoriaRolesPort, EventosRolesPort, SesionesRolesPort {

    private final AuditoriaPort auditoria;
    private final MensajeriaPort mensajeria;
    private final SesionesPort sesiones;

    public EfectosRolesAdapter(AuditoriaPort auditoria, MensajeriaPort mensajeria, SesionesPort sesiones) {
        this.auditoria = auditoria;
        this.mensajeria = mensajeria;
        this.sesiones = sesiones;
    }

    @Override
    public void registrarCritica(RegistroRol registro) {
        auditoria.registrarCritica(aSeguro(registro));
    }

    @Override
    public void registrarNoCritica(RegistroRol registro) {
        auditoria.registrarNoCriticaTrasCommit(aSeguro(registro));
    }

    @Override
    public void guardar(RolesCambiadosEvento evento) {
        mensajeria.guardarEvento(new EventoUsuario(evento.eventoId(), RolesCambiadosEvento.TIPO, Map.of(
                "usuarioId", evento.usuarioId().toString(),
                "roles", evento.roles().stream().map(Rol::name).collect(Collectors.joining(",")),
                "fecha", evento.fecha().toString())));
    }

    @Override
    public void revocarTodas(UUID usuarioId) {
        sesiones.revocarTodas(usuarioId);
    }

    private static RegistroSeguro aSeguro(RegistroRol r) {
        Map<String, String> detalle = new HashMap<>();
        if (r.rol() != null) {
            detalle.put("rol", r.rol().name());
        }
        return new RegistroSeguro(r.registroId(), r.accion().name(), r.resultado(), "USUARIO", r.actor().toString(),
                r.objetivo(), r.fecha(), r.contexto().ip(), r.contexto().agente(), detalle);
    }
}
