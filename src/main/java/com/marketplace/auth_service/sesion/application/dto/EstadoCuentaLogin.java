package com.marketplace.auth_service.sesion.application.dto;

/** Estado de cuenta relevante para el login (lo resuelve Eva). */
public enum EstadoCuentaLogin {
    ACTIVA,
    PENDIENTE_VERIFICACION,
    INACTIVA,
    BLOQUEADA
}
