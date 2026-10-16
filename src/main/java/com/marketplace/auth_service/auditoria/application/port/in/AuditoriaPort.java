package com.marketplace.auth_service.auditoria.application.port.in;

import com.marketplace.auth_service.auditoria.application.dto.PaginaActividad;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import java.util.UUID;

/**
 * Capacidad exportada de auditoría (ACUERDOS-INTERNOS §2, SPEC-12).
 * Los productores (Luis, Sergio) la consumen solo por este puerto.
 *
 * <p>Crítica confirma junto al hecho en su transacción; si el registro falla,
 * la operación revierte (RF-12.6). No crítica se intenta tras el commit con
 * reintento privado. Solo expone DTO propios y tipos Java.
 *
 * <p>EP-06-T001 entrega las firmas; T002/T003 implementan la transaccionalidad
 * y T004 la consulta.
 */
public interface AuditoriaPort {

    /**
     * Registra una acción crítica dentro de la transacción del productor
     * (MANDATORY, sin REQUIRES_NEW). El productor decide hecho/actor/objetivo;
     * aquí se valida formato y catálogo.
     */
    void registrarCritica(RegistroSeguro registro);

    /**
     * Registra una acción no crítica después del commit del productor, con
     * reintento durable y deduplicación por registroId. Su fallo nunca revierte
     * el hecho ya confirmado.
     */
    void registrarNoCriticaTrasCommit(RegistroSeguro registro);

    /**
     * Actividad del titular: objetivo siempre igual al sub validado, orden
     * fecha descendente y paginación/total reales (EP-06-T004).
     */
    PaginaActividad actividadPropia(UUID titular, int pagina, int tamano);
}
