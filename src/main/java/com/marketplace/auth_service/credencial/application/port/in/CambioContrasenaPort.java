package com.marketplace.auth_service.credencial.application.port.in;

import com.marketplace.auth_service.credencial.application.dto.ResultadoCambioContrasena;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.UUID;

/**
 * Cambio de contraseña conociendo la actual (SPEC-07, EP-03-T005). Exige la actual, valida la
 * nueva con la política vigente, la registra en el historial y revoca las demás sesiones,
 * conservando la del acceso {@code jti} que hizo el cambio.
 */
public interface CambioContrasenaPort {
    ResultadoCambioContrasena cambiar(UUID usuarioId, UUID jti, SecretoEfimero actual, SecretoEfimero nueva,
                                      ContextoPeticion contexto);
}
