package com.marketplace.auth_service.auditoria.application.port.out;

import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import java.util.List;
import java.util.UUID;

/**
 * Lo que el caso de uso de auditoría necesita de persistencia (EP-06-T001/T002/T004).
 * La tabla es de solo anexado: sin modificar ni borrar desde la aplicación.
 */
public interface AuditoriaPersistenciaPort {

    /** Persiste un registro ya validado; devuelve su id numérico. */
    long guardar(RegistroAuditoria registro);

    /** ¿Ya existe un registro con ese identificador interno? (deduplicación). */
    boolean existeRegistro(UUID registroId);

    /** Registros cuyo objetivo es el titular, fecha descendente; omite históricos no proyectables. */
    Pagina listarDeObjetivo(UUID objetivo, int pagina, int tamano);

    record Pagina(List<RegistroAuditoria> registros, long total) {
        public Pagina {
            registros = List.copyOf(registros);
        }
    }
}
