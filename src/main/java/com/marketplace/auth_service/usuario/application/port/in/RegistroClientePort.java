package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.dto.SolicitudRegistro;
import java.util.List;

/** Registro público de clientes (SPEC-01, FEAT-01-T1). Abre su propia transacción. */
public interface RegistroClientePort {
    ResultadoRegistro registrar(SolicitudRegistro solicitud, ContextoPeticion contexto);

    /**
     * Audita una solicitud rechazada por forma (400) antes del alta (RF-01.6): solo los
     * nombres de los campos inválidos, nunca sus valores.
     *
     * @param canalOrigen canal recibido si es válido, o {@code null}
     */
    void auditarSolicitudInvalida(List<String> campos, String canalOrigen, ContextoPeticion contexto);
}
