package com.marketplace.auth_service.credencial.application.port.out;

import com.marketplace.auth_service.credencial.application.dto.CuentaRecuperacion;
import java.time.Instant;

/** Emite el token y guarda el correo cifrado en el outbox del productor. */
public interface CorreoRecuperacionPort {
    void emitirYEnviar(CuentaRecuperacion cuenta, Instant ahora);
}
