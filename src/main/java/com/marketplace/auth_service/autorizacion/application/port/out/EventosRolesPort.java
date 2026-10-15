package com.marketplace.auth_service.autorizacion.application.port.out;

import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiadosEvento;

/**
 * Outbox del evento {@code usuario.roles_cambiados} (RF-11.6). Se cumplirá con
 * un puente hacia {@code MensajeriaPort.guardarEvento} de Christian: se guarda
 * en el commit del cambio y se publica en RabbitMQ después, fuera de los locks.
 */
public interface EventosRolesPort {

    /** Guarda el evento en el outbox. Requiere transacción. */
    void guardar(RolesCambiadosEvento evento);
}
