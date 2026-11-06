package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.perfil.application.port.out.EfectosCambioCorreoPort;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Abre la transacción de la solicitud (REQUIRED, READ_COMMITTED), como el resto de coordinadores. */
public final class EfectosCambioCorreoAdapter implements EfectosCambioCorreoPort {

    private final TransactionTemplate transaccion;

    public EfectosCambioCorreoAdapter(PlatformTransactionManager transacciones) {
        this.transaccion = new TransactionTemplate(Objects.requireNonNull(transacciones));
        this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public <T> T enTransaccion(Supplier<T> trabajo) {
        return transaccion.execute(estado -> trabajo.get());
    }
}
