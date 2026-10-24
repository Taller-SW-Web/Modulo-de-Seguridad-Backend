package com.marketplace.auth_service.credencial.application.port.out;

import com.marketplace.auth_service.credencial.application.dto.CuentaRecuperacion;
import java.util.Optional;

/** Consulta delegada al dueño de cuentas; no accede a su repositorio físico. */
public interface CuentasRecuperacionPort {
    Optional<CuentaRecuperacion> buscar(String correoNormalizado);
}
