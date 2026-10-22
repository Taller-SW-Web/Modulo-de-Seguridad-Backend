package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.dto.SolicitudRegistro;

/** Registro público de clientes (SPEC-01, FEAT-01-T1). Abre su propia transacción. */
public interface RegistroClientePort {
    ResultadoRegistro registrar(SolicitudRegistro solicitud, ContextoPeticion contexto);
}
