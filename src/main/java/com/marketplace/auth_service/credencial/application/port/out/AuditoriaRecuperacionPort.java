package com.marketplace.auth_service.credencial.application.port.out;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.UUID;

/** Registra la solicitud sin guardar correo, token ni su HMAC en el detalle. */
public interface AuditoriaRecuperacionPort {
    void registrar(UUID objetivoUsuarioId, boolean aceptada, ContextoPeticion contexto, Instant ahora);
}
