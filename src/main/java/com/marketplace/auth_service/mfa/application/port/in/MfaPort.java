package com.marketplace.auth_service.mfa.application.port.in;

import com.marketplace.auth_service.mfa.application.dto.EstadoMfa;
import com.marketplace.auth_service.mfa.application.dto.ResultadoCreacionDesafio;
import com.marketplace.auth_service.mfa.application.dto.RevisionCredencialMfa;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Capacidad exportada de T002: solo DTO/Java, sin entidades ni dominio ajenos.
 * Roles se obtienen del proveedor real, nunca de datos enviados por el navegador.
 * Crear exige la transacción del login con cuenta bloqueada y contraseña preverificada.
 * Solicitar/verificar OTP se implementarán en T003/T004, sin métodos ficticios aquí.
 */
public interface MfaPort {
    boolean esEfectivo(boolean voluntario, Set<String> roles);
    ResultadoCreacionDesafio crearDesafio(UUID usuarioId, RevisionCredencialMfa revision);
    Optional<EstadoMfa> leerEstado(UUID usuarioId, Set<String> rolesActuales);
}
