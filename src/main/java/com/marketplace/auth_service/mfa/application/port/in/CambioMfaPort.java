package com.marketplace.auth_service.mfa.application.port.in;

import com.marketplace.auth_service.mfa.application.dto.ResultadoCambioMfa;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

public interface CambioMfaPort {
    ResultadoCambioMfa solicitar(ActorUsuario actor, String accion, ContextoPeticion contexto);
    ResultadoCambioMfa reenviar(ActorUsuario actor, String token, ContextoPeticion contexto);
    ResultadoCambioMfa confirmar(ActorUsuario actor, String token, String codigo, ContextoPeticion contexto);
}
