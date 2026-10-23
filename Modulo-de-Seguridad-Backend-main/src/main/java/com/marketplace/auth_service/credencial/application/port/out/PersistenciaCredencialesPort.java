package com.marketplace.auth_service.credencial.application.port.out;

import com.marketplace.auth_service.credencial.application.dto.RevisionCredencial;
import com.marketplace.auth_service.credencial.domain.model.Credencial;
import com.marketplace.auth_service.credencial.domain.model.PasswordHistorial;
import java.util.Optional;
import java.util.UUID;

public interface PersistenciaCredencialesPort {
    Optional<Credencial> leer(UUID usuarioId);
    boolean coincideRevision(RevisionCredencial revision);
    PasswordHistorial historial(UUID usuarioId, Credencial actual);
}
