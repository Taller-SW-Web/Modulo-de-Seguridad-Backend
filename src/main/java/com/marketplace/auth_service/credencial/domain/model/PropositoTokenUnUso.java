package com.marketplace.auth_service.credencial.domain.model;

/** Propósitos funcionales propios de credenciales; DESBLOQUEO_AUTOMATICO se almacena como DESBLOQUEO. */
public enum PropositoTokenUnUso {
    VERIFICAR_CORREO,
    CAMBIO_CORREO,
    RECUPERACION,
    DESBLOQUEO_AUTOMATICO,
    ACTIVACION
}
