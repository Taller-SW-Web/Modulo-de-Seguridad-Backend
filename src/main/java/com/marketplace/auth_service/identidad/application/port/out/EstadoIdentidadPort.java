package com.marketplace.auth_service.identidad.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que la introspección necesita saber hoy de otros dueños. La implementan
 * puentes en {@code adapter.out.capacidad} sobre los puertos exportados de
 * sesiones (Jose Luis), cuentas (Eva) y autorización.
 */
public interface EstadoIdentidadPort {

    /** ¿El acceso {@code jti} de {@code sub} sigue asociado a una familia no revocada (DAT-01)? */
    boolean accesoVigente(UUID sub, UUID jti);

    /** Estado efectivo y correo actuales; vacío si la cuenta no existe. */
    Optional<Cuenta> cuenta(UUID sub);

    /** Roles y permisos actuales de la base, en orden canónico. */
    Permisos permisos(UUID sub);

    record Cuenta(String estadoEfectivo, String correo) { }

    record Permisos(List<String> roles, List<String> permisos) {
        public Permisos {
            roles = List.copyOf(roles);
            permisos = List.copyOf(permisos);
        }
    }
}
