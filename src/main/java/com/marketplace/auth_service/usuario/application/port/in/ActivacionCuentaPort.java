package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoActivacion;

/** Activación de una cuenta creada por otra persona (SPEC-03 RF-03.5, FEAT-13-T1). Público. */
public interface ActivacionCuentaPort {
    /** El titular ya aceptó los términos: el adaptador HTTP lo exige antes de llamar. */
    ResultadoActivacion activar(String tokenClaro, String contrasena, ContextoPeticion contexto);
}
