package com.marketplace.auth_service.usuario.application.dto;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.Set;
public record FiltroCuenta(String estado, String rol, String correoFragmento) {
    public FiltroCuenta {
        if (estado != null && !Set.of("ACTIVO", "INACTIVO", "BLOQUEADO", "PENDIENTE_VERIFICACION").contains(estado))
            throw new IllegalArgumentException("Estado de cuenta inválido");
        if (rol != null) Rol.valueOf(rol);
        if (correoFragmento != null && correoFragmento.length() > 254)
            throw new IllegalArgumentException("Fragmento de correo demasiado largo");
    }
}
