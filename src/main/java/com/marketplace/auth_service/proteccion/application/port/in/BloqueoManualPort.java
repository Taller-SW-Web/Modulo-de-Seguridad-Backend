package com.marketplace.auth_service.proteccion.application.port.in;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

public interface BloqueoManualPort {
    /** null indica éxito; los demás valores son códigos del catálogo publicado. */
    String ejecutar(ActorUsuario actor, String objetivo, boolean bloquear, String motivo, ContextoPeticion contexto);
}
