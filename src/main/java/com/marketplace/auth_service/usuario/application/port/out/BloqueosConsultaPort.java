package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.usuario.application.dto.BloqueoVista;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bloqueos vigentes de las cuentas pedidas; el dueño de la tabla bloqueo es protección. */
public interface BloqueosConsultaPort {
    Map<UUID, BloqueoVista> vigentes(Set<UUID> usuarios);
}
