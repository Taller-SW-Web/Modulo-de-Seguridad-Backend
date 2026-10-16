package com.marketplace.auth_service.auditoria.adapter.out.transaccion;

import com.marketplace.auth_service.auditoria.application.port.out.TareasTrasCommitPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** afterCommit + REQUIRES_NEW; un fallo se registra (sin datos del hecho) y no se propaga. */
public final class TareasTrasCommitSpring implements TareasTrasCommitPort {

    private static final Logger log = LoggerFactory.getLogger(TareasTrasCommitSpring.class);

    private final TransactionTemplate propia;

    public TareasTrasCommitSpring(PlatformTransactionManager transacciones) {
        this.propia = new TransactionTemplate(transacciones);
        this.propia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void trasCommit(Runnable tarea) {
        Runnable segura = () -> {
            try {
                propia.executeWithoutResult(estado -> tarea.run());
            } catch (RuntimeException e) {
                log.warn("auditoría no crítica no registrada ({})", e.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    segura.run();
                }
            });
        } else {
            segura.run();
        }
    }
}
