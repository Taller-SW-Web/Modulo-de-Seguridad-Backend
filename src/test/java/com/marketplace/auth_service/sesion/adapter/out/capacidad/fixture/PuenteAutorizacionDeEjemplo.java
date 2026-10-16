package com.marketplace.auth_service.sesion.adapter.out.capacidad.fixture;

import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;

import java.util.List;
import java.util.UUID;

/**
 * SOLO PRUEBA. Cómo consume autorización otra capacidad (aquí, sesiones al
 * emitir un acceso): desde un puente en su {@code adapter.out.capacidad}, con
 * el puerto exportado, su DTO y los tipos comunes. Compila contra el contrato
 * real, así un cambio incompatible del puerto rompe esta prueba.
 */
public class PuenteAutorizacionDeEjemplo {

    private final AutorizacionPort autorizacion;

    public PuenteAutorizacionDeEjemplo(AutorizacionPort autorizacion) {
        this.autorizacion = autorizacion;
    }

    /** Claims {@code roles} y {@code permisos} del acceso que firmará identidad. */
    public List<List<String>> claimsDe(UUID usuarioId) {
        PermisosResueltos actuales = autorizacion.resolver(usuarioId);
        return List.of(actuales.codigosRoles(), actuales.codigosPermisos());
    }

    public boolean puedeVerUsuarios(UUID usuarioId) {
        return autorizacion.resolver(usuarioId).tiene(Permiso.USUARIO_VER);
    }

    public boolean exigeSegundoFactor(UUID usuarioId) {
        return autorizacion.resolver(usuarioId).roles().stream().anyMatch(Rol::esDeGestion);
    }
}
