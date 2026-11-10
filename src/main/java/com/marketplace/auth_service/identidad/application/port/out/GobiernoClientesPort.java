package com.marketplace.auth_service.identidad.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.time.Instant;
import java.util.Map;

/**
 * Lo que la gestión de clientes técnicos necesita de autorización y auditoría
 * (SPEC-19). Lo implementa un puente en {@code adapter.out.capacidad}.
 */
public interface GobiernoClientesPort {

    /** ¿Tiene hoy, según la base, {@code ADMIN_SISTEMA} con {@code integracion.gestionar}? */
    boolean autorizado(ActorUsuario actor);

    /** Registra una acción crítica en la transacción en curso; si falla, la operación revierte. */
    void registrar(String accion, ActorUsuario actor, ContextoPeticion contexto, Instant fecha,
                   Map<String, String> detalle);

    /** Registra {@code ACCESO_DENEGADO} tras el commit; su fallo no cambia el 403. */
    void denegado(ActorUsuario actor, ContextoPeticion contexto, Instant fecha);
}
