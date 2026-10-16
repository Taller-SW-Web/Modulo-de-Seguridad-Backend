package com.marketplace.auth_service.auditoria.application.port.out;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;

/**
 * Cola privada durable de reintentos de auditoría (EP-06-T003,
 * {@code seguridad.auditoria.pendiente}). No es evento público ni suscripción
 * G5: solo el worker propio la consume. Deduplicable por registroId.
 */
public interface ReintentoAuditoriaPort {

    /** Difiere un registro no crítico fallado para reintentarlo. */
    void diferir(RegistroSeguro registro);
}
