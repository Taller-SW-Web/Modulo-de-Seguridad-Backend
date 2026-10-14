package com.marketplace.auth_service.autorizacion.application.dto;

import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de auditoría que produce la gestión de roles (SPEC-12). El puente
 * hacia {@code AuditoriaPort} (Christian) lo convierte en su {@code RegistroSeguro}.
 *
 * @param registroId identificador interno para deduplicar
 * @param accion     {@code ROL_ASIGNADO}, {@code ROL_REVOCADO} o {@code ACCESO_DENEGADO}
 * @param actor      quien hizo la petición
 * @param objetivo   cuenta afectada
 * @param rol        rol concedido o retirado; {@code null} en un acceso denegado
 * @param fecha      momento del hecho
 * @param contexto   IP y correlación de la petición
 */
public record RegistroRol(UUID registroId, Accion accion, UUID actor, UUID objetivo, Rol rol,
                          Instant fecha, ContextoPeticion contexto) {

    /** Acciones del catálogo de auditoría que produce esta capacidad. */
    public enum Accion {
        /** Crítica: comparte commit con la asignación (ESC-12.6). */
        ROL_ASIGNADO,
        /** Crítica: comparte commit con la revocación. */
        ROL_REVOCADO,
        /** No crítica: se registra después, aunque la operación se rechazó (ESC-11.3). */
        ACCESO_DENEGADO
    }

    public RegistroRol {
        Objects.requireNonNull(registroId, "registroId");
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(objetivo, "objetivo");
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(contexto, "contexto");
    }

    /** {@code EXITO} para un cambio hecho; {@code FALLO} para un acceso denegado. */
    public String resultado() {
        return accion == Accion.ACCESO_DENEGADO ? "FALLO" : "EXITO";
    }
}
