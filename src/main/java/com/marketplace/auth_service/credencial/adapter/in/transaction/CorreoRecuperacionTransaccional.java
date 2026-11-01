package com.marketplace.auth_service.credencial.adapter.in.transaction;

import com.marketplace.auth_service.credencial.application.dto.CuentaRecuperacion;
import com.marketplace.auth_service.credencial.application.dto.CanalOrigenRecuperacion;
import com.marketplace.auth_service.credencial.application.port.out.CorreoRecuperacionPort;
import java.time.Instant;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Confirma token y outbox juntos, aislados de la transacción que reserva la cuota. */
public final class CorreoRecuperacionTransaccional implements CorreoRecuperacionPort {
    private final CorreoRecuperacionPort delegado;
    private final TransactionTemplate transaccion;

    public CorreoRecuperacionTransaccional(CorreoRecuperacionPort delegado,
                                           PlatformTransactionManager gestorTransacciones) {
        this.delegado = Objects.requireNonNull(delegado);
        this.transaccion = new TransactionTemplate(Objects.requireNonNull(gestorTransacciones));
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void emitirYEnviar(CuentaRecuperacion cuenta, CanalOrigenRecuperacion canalOrigen, Instant ahora) {
        transaccion.executeWithoutResult(estado -> delegado.emitirYEnviar(cuenta, canalOrigen, ahora));
    }
}
