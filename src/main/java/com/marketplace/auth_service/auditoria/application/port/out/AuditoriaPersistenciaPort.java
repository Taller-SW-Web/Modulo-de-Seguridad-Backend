package com.marketplace.auth_service.auditoria.application.port.out;

import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import java.util.UUID;

/**
 * Lo que el caso de uso de auditoría necesita de persistencia (EP-06-T001).
 * La tabla es de solo anexado: sin modificar ni borrar desde la aplicación.
 */
public interface AuditoriaPersistenciaPort {

    /** Persiste un registro ya validado; devuelve su id numérico. */
    long guardar(RegistroAuditoria registro);

    /** ¿Ya existe un registro con ese identificador interno? (deduplicación). */
    boolean existeRegistro(UUID registroId);
}
