package com.marketplace.auth_service.credencial.adapter.in.transaction;

import com.marketplace.auth_service.credencial.application.dto.ResultadoSolicitudRecuperacion;
import com.marketplace.auth_service.credencial.application.port.in.SolicitarRecuperacionPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.Objects;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** La cuota, el token, el correo outbox y la auditoría participan del mismo commit. */
public class SolicitarRecuperacionTransaccional implements SolicitarRecuperacionPort {
    private final SolicitarRecuperacionPort delegado;

    public SolicitarRecuperacionTransaccional(SolicitarRecuperacionPort delegado) {
        this.delegado = Objects.requireNonNull(delegado);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoSolicitudRecuperacion solicitar(String correo, ContextoPeticion contexto) {
        return delegado.solicitar(correo, contexto);
    }
}
