package com.marketplace.auth_service.credencial.application.port.in;

import com.marketplace.auth_service.credencial.application.dto.ResultadoSolicitudRecuperacion;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

/** Solicita un enlace público de recuperación (SPEC-08, FEAT-03-T2). */
public interface SolicitarRecuperacionPort {
    ResultadoSolicitudRecuperacion solicitar(String correo, ContextoPeticion contexto);
}
