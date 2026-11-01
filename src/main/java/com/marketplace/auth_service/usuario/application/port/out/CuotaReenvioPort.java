package com.marketplace.auth_service.usuario.application.port.out;

import java.time.Duration;
import java.time.Instant;

/** Límite de 3 reenvíos por hora por correo, exista o no la cuenta (RF-02.4). Requiere transacción. */
public interface CuotaReenvioPort {
    /** Reserva una solicitud y devuelve {@link Duration#ZERO}; si no quedan, la espera hasta la siguiente. */
    Duration reservar(String correoNormalizado, Instant ahora);
}
