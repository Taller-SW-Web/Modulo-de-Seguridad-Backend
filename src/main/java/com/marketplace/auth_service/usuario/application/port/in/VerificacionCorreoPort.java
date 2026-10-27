package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoReenvio;
import com.marketplace.auth_service.usuario.application.dto.ResultadoVerificacion;

/** Verificación de correo y reenvío del enlace (SPEC-02, FEAT-01-T3). Abre su propia transacción. */
public interface VerificacionCorreoPort {

    /** Consume el token del enlace y activa la cuenta {@code PENDIENTE_VERIFICACION}. */
    ResultadoVerificacion verificar(String tokenClaro, ContextoPeticion contexto);

    /** Reenvía el enlace si hay una cuenta pendiente con ese correo; la cuota cuenta igual si no la hay. */
    ResultadoReenvio reenviar(String correo, ContextoPeticion contexto);
}
