package com.marketplace.auth_service.credencial.application.port.out;

import java.time.Duration;
import java.time.Instant;

/** Reserva atómicamente una de tres solicitudes por correo y hora. Cero significa cuota reservada. */
public interface CuotaRecuperacionPort {
    Duration reservar(String correoNormalizado, Instant ahora);
}
