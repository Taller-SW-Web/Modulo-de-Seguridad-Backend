package com.marketplace.auth_service.proteccion.application.port.in;
import com.marketplace.auth_service.proteccion.application.dto.*;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.UUID;
public interface ProteccionPort {
    EvaluacionProteccion evaluar(CuentaSnapshot cuenta, Instant ahora);
    /** Transacción existente; caller ya tomó guard ADMIN y cuenta, y confirmó credencial. */
    ResultadoFallo registrarFallo(CuentaSnapshot cuenta, HuellaFallo huella, ContextoPeticion contexto);
    /** Solo autenticación completa; nunca al emitir un desafío. Cuenta ya bloqueada. */
    void completarLogin(UUID usuarioId);
    ResultadoDesbloqueo desbloquear(PruebaEnlace prueba, ContextoPeticion contexto);
    /** El productor ya bloqueó la cuenta; fija el orden cuenta → bloqueo/racha → token. */
    void prepararRestablecimiento(UUID usuarioId);
    /** Levanta únicamente el bloqueo automático y reinicia los intentos fallidos. */
    void restablecerPorRecuperacion(UUID usuarioId, ContextoPeticion contexto, Instant ahora);
}
