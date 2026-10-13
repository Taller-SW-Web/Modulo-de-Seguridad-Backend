package com.marketplace.auth_service.autorizacion.application.port.out;

import com.marketplace.auth_service.autorizacion.application.dto.RegistroRol;

/**
 * Auditoría de la gestión de roles (RF-11.8, SPEC-12). Se cumplirá con un puente
 * hacia {@code AuditoriaPort} de Christian.
 */
public interface AuditoriaRolesPort {

    /**
     * {@code ROL_ASIGNADO}/{@code ROL_REVOCADO}: crítica, en la transacción del
     * cambio. Si no se puede guardar, el cambio entero se revierte (ESC-12.6).
     */
    void registrarCritica(RegistroRol registro);

    /**
     * {@code ACCESO_DENEGADO}: no crítica, con su propio intento y reintentos,
     * fuera de la transacción rechazada (ESC-11.3).
     */
    void registrarNoCritica(RegistroRol registro);
}
