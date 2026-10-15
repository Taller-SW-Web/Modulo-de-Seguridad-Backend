package com.marketplace.auth_service.credencial.application.port.out;

import com.marketplace.auth_service.credencial.application.dto.DatosPersonalesPassword;
import java.util.UUID;

public interface DatosPersonalesPasswordPort {
    DatosPersonalesPassword leer(UUID usuarioId);
}
