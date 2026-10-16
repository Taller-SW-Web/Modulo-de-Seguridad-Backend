package com.marketplace.auth_service.auditoria.adapter.out.transaccion;

import com.marketplace.auth_service.auditoria.application.port.out.TareasTrasCommitPort;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tras el commit, en su propia transacción y en un ejecutor aparte. En {@code afterCommit} la
 * conexión del productor sigue tomada: abrir ahí otra transacción en el mismo hilo pedía una
 * segunda conexión y, con el pool de 5 lleno de productores, se interbloqueaba hasta el timeout
 * (medido en la prueba de carga EP-02-T007). Un fallo o una cola llena se registran y no afectan
 * al hecho ya confirmado (registro no crítico).
 */
public final class TareasTrasCommitSpring implements TareasTrasCommitPort {

    private static final Logger log = LoggerFactory.getLogger(TareasTrasCommitSpring.class);

    private final TransactionTemplate propia;
    private final ThreadPoolExecutor ejecutor;

    public TareasTrasCommitSpring(PlatformTransactionManager transacciones) {
        this.propia = new TransactionTemplate(transacciones);
        this.propia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AtomicInteger n = new AtomicInteger();
        this.ejecutor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10_000), r -> {
            Thread t = new Thread(r, "auditoria-no-critica-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }, (tarea, ex) -> log.warn("auditoría no crítica descartada: cola llena"));
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
                    ejecutor.execute(segura);
                }
            });
        } else {
            ejecutor.execute(segura);
        }
    }
}
