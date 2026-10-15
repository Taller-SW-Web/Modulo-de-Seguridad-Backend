package com.marketplace.auth_service.sesion.application.dto;

/**
 * Por qué se rechaza una renovación. Todas las variantes responden el mismo
 * {@code 401 REFRESCO_INVALIDO}: la respuesta no revela si el refresco existe,
 * venció, se revocó o se reutilizó.
 */
public enum MotivoRechazo {
    REFRESCO_DESCONOCIDO,
    REFRESCO_VENCIDO,
    REFRESCO_REVOCADO,
    REUSO_DETECTADO,
    CUENTA_NO_DISPONIBLE
}
