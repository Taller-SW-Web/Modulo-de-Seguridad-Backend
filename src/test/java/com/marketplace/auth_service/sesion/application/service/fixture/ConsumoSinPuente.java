package com.marketplace.auth_service.sesion.application.service.fixture;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;

import java.util.UUID;

/** SOLO PRUEBA, INVÁLIDO A PROPÓSITO: el núcleo de otra capacidad llamando a autorización sin puente. */
public class ConsumoSinPuente {

    private final AutorizacionPort autorizacion;

    public ConsumoSinPuente(AutorizacionPort autorizacion) {
        this.autorizacion = autorizacion;
    }

    public boolean tieneRoles(UUID usuarioId) {
        return !autorizacion.resolver(usuarioId).roles().isEmpty();
    }
}
