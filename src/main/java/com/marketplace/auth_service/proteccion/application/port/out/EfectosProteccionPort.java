package com.marketplace.auth_service.proteccion.application.port.out;
import com.marketplace.auth_service.proteccion.application.dto.*;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.UUID;
/** Requiere proveedor real: crítica+evento+correo cifrado durable en el mismo
 * commit; no transporte bajo locks. No implementar como log/no-op en producción. */
public interface EfectosProteccionPort {
    void bloqueada(CuentaSnapshot cuenta, UUID revision, Instant hasta, EnlaceEmitido enlace,
            ContextoPeticion contexto, Instant ahora);
    void desbloqueada(CuentaSnapshot cuenta, UUID revision, ContextoPeticion contexto, Instant ahora);
    void desbloqueadaPorRestablecimiento(CuentaSnapshot cuenta, UUID revision,
            ContextoPeticion contexto, Instant ahora);
}
