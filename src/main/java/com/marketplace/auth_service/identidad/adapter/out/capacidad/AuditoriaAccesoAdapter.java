package com.marketplace.auth_service.identidad.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.identidad.application.dto.DenegacionAcceso;
import com.marketplace.auth_service.identidad.application.port.out.AuditoriaAccesoPort;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Puente hacia auditoría: un 403 de la frontera es {@code ACCESO_DENEGADO} con
 * resultado {@code FALLO}, no crítico. El detalle lleva motivo, método, ruta y
 * jti; nunca el token ni el scope que faltaba (ESC-17.4, ESC-17.8).
 */
public final class AuditoriaAccesoAdapter implements AuditoriaAccesoPort {

    private final AuditoriaPort auditoria;

    public AuditoriaAccesoAdapter(AuditoriaPort auditoria) {
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
    }

    @Override
    public void accesoDenegado(DenegacionAcceso d) {
        Map<String, String> detalle = new HashMap<>();
        detalle.put("motivo", d.motivo());
        detalle.put("metodo", d.metodo());
        detalle.put("ruta", d.ruta());
        if (d.jti() != null) {
            detalle.put("jti", d.jti().toString());
        }
        auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "ACCESO_DENEGADO", "FALLO",
                d.actorTipo(), d.actorId(), null, d.fecha(), d.contexto().ip(), d.contexto().agente(), detalle));
    }
}
