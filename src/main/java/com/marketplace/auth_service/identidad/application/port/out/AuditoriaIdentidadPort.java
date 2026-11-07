package com.marketplace.auth_service.identidad.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Puente hacia auditoría (SPEC-18 RF-18.6): el dueño del registro central es {@code auditoria}. */
public interface AuditoriaIdentidadPort {

    /** No crítica: registra cada acceso de un módulo consumidor (RF-18.6). */
    void moduloConsultoUsuario(String clientId, UUID objetivo, Instant fecha);

    /** Crítica: el documento salió en claro (RF-18.4/ESC-18.5). */
    void moduloObtuvoDocumento(String clientId, UUID objetivo, Instant fecha);
}
