package com.marketplace.auth_service.identidad.application.port.out;

import com.marketplace.auth_service.identidad.domain.model.ClienteServicio;

import java.util.Optional;

/** Lectura de clientes de servicio y sus scopes concedidos con su audiencia. Sin escritura. */
public interface ClientesServicioPort {

    Optional<ClienteServicio> buscar(String clientId);
}
