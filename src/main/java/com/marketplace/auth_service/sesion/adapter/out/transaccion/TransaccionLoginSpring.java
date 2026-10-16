package com.marketplace.auth_service.sesion.adapter.out.transaccion;

import com.marketplace.auth_service.sesion.application.port.out.TransaccionLoginPort;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** READ_COMMITTED con commit también en rechazos: el fallo contado persiste antes del 401. */
public final class TransaccionLoginSpring implements TransaccionLoginPort {

    private final TransactionTemplate plantilla;

    public TransaccionLoginSpring(PlatformTransactionManager transacciones) {
        this.plantilla = new TransactionTemplate(transacciones);
        plantilla.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        plantilla.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return plantilla.execute(estado -> trabajo.get());
    }
}
