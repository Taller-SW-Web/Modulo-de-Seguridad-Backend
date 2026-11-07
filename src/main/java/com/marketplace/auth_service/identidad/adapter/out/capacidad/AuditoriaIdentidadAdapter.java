package com.marketplace.auth_service.identidad.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.identidad.application.port.out.AuditoriaIdentidadPort;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Puente hacia auditoría (SPEC-18 RF-18.6): cada acceso de un módulo
 * consumidor es {@code MODULO_CONSULTO_USUARIO}, no crítico; si el documento
 * salió en claro, además {@code MODULO_OBTUVO_DOCUMENTO}, crítico. El detalle
 * solo lleva el endpoint, nunca el documento (ni enmascarado).
 */
public final class AuditoriaIdentidadAdapter implements AuditoriaIdentidadPort {

    static final String ENDPOINT = "GET /api/v1/usuarios/{id}";

    private final AuditoriaPort auditoria;

    public AuditoriaIdentidadAdapter(AuditoriaPort auditoria) {
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
    }

    @Override
    public void moduloConsultoUsuario(String clientId, UUID objetivo, Instant fecha) {
        auditoria.registrarNoCriticaTrasCommit(registro("MODULO_CONSULTO_USUARIO", clientId, objetivo, fecha));
    }

    @Override
    public void moduloObtuvoDocumento(String clientId, UUID objetivo, Instant fecha) {
        auditoria.registrarCritica(registro("MODULO_OBTUVO_DOCUMENTO", clientId, objetivo, fecha));
    }

    private static RegistroSeguro registro(String accion, String clientId, UUID objetivo, Instant fecha) {
        return new RegistroSeguro(UUID.randomUUID(), accion, "EXITO", "MODULO", clientId, objetivo, fecha, null,
                null, Map.of("endpoint", ENDPOINT));
    }
}
