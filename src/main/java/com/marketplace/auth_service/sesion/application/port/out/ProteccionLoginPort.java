package com.marketplace.auth_service.sesion.application.port.out;

import com.marketplace.auth_service.sesion.application.dto.SecretoEfimero;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.util.UUID;

/**
 * Defensa para el login (Luis, SPEC-14): solo la incorrecta sobre cuenta
 * ACTIVA suma racha; el login completo la resetea. Dueño del contador y del
 * bloqueo; aquí no se decide ni se cuenta nada.
 */
public interface ProteccionLoginPort {
    boolean loginBloqueado(UUID usuarioId);
    void registrarFallo(UUID usuarioId, SecretoEfimero intento, ContextoPeticion contexto);
    void completarLogin(UUID usuarioId);
}
