package com.marketplace.auth_service.perfil.application.port.out;

import java.util.function.Supplier;

/** Transacción de la solicitud: cuenta bloqueada, token y correos encolados se confirman o se descartan juntos. */
public interface EfectosCambioCorreoPort {
    <T> T enTransaccion(Supplier<T> trabajo);
}
