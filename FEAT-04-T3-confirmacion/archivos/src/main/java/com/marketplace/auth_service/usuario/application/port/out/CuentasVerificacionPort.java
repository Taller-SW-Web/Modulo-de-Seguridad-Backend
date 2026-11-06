package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.usuario.application.dto.ResultadoAplicacionCorreo;
import com.marketplace.auth_service.usuario.domain.model.CuentaPendiente;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Estado de verificación de las cuentas. Requiere la transacción del llamador. */
public interface CuentasVerificacionPort {

    /**
     * Bloquea la fila y, si la cuenta sigue {@code PENDIENTE_VERIFICACION}, la pasa a {@code ACTIVO}
     * con el correo verificado. {@code false} si no existe o ya no está pendiente.
     */
    boolean activarSiPendiente(UUID usuarioId, Instant ahora);

    /**
     * Bloquea la fila y reemplaza el correo de la cuenta por el confirmado, ya verificado (SPEC-16,
     * ESC-16.3). Si otra cuenta ya tiene esa dirección no cambia nada. El correo se compara y guarda
     * normalizado (minúsculas).
     */
    ResultadoAplicacionCorreo aplicarCambioCorreo(UUID usuarioId, String correoNuevo, Instant ahora);

    /** La cuenta con ese correo normalizado, solo si sigue pendiente de verificar. */
    Optional<CuentaPendiente> buscarPendiente(String correoNormalizado);
}
