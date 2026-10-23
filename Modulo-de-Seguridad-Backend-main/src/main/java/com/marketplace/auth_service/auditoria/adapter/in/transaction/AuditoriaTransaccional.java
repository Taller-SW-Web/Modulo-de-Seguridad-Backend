package com.marketplace.auth_service.auditoria.adapter.in.transaction;

import com.marketplace.auth_service.auditoria.application.dto.PaginaActividad;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Único AuditoriaPort publicado: la crítica exige la transacción del productor (sin REQUIRES_NEW). */
public class AuditoriaTransaccional implements AuditoriaPort {
    private final AuditoriaPort delegado;

    public AuditoriaTransaccional(AuditoriaPort delegado) {
        this.delegado = Objects.requireNonNull(delegado);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void registrarCritica(RegistroSeguro registro) {
        delegado.registrarCritica(registro);
    }

    @Override @Transactional(propagation = Propagation.SUPPORTS)
    public void registrarNoCriticaTrasCommit(RegistroSeguro registro) {
        delegado.registrarNoCriticaTrasCommit(registro);
    }

    @Override @Transactional(readOnly = true)
    public PaginaActividad actividadPropia(UUID titular, int pagina, int tamano) {
        return delegado.actividadPropia(titular, pagina, tamano);
    }
}
