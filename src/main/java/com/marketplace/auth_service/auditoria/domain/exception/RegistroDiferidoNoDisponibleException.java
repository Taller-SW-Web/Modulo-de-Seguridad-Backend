package com.marketplace.auth_service.auditoria.domain.exception;

/**
 * El intento independiente tras commit falló y aún no hay cola de reintento
 * (el worker de TEC-INFRA/EP-06-T003 la provee). No se afirma escritura ni se
 * revierte el hecho ya confirmado; el ejecutor lo registra como incidente.
 */
public class RegistroDiferidoNoDisponibleException extends RuntimeException {
    public RegistroDiferidoNoDisponibleException(Throwable causa) {
        super("No se pudo registrar la auditoría no crítica y no hay reintento disponible", causa);
    }
}
