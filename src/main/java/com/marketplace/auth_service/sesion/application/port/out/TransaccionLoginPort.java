package com.marketplace.auth_service.sesion.application.port.out;

import java.util.function.Supplier;

/** Una transacción READ_COMMITTED que confirma también cuando el resultado es un rechazo. */
public interface TransaccionLoginPort {
    <T> T ejecutar(Supplier<T> trabajo);
}
