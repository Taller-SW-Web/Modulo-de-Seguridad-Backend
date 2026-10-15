package com.marketplace.auth_service.proteccion.application.port.out;
import com.marketplace.auth_service.proteccion.application.dto.CuentaSnapshot;
import java.util.Optional;
import java.util.UUID;
public interface CuentasProteccionPort {
    Optional<CuentaSnapshot> leer(UUID usuarioId);
    CuentaSnapshot bloquear(UUID usuarioId);
    void bloquearCatalogoAdministrador();
    void guardarContadores(UUID usuarioId, int bloqueosSeguidos, int intentos);
}
