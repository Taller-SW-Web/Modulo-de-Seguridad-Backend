package com.marketplace.auth_service.auditoria.adapter.out.persistence;

import com.marketplace.auth_service.auditoria.adapter.out.persistence.mapper.AuditoriaPersistenceMapper;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.repository.AuditoriaSeguridadJpaRepository;
import com.marketplace.auth_service.auditoria.application.port.out.AuditoriaPersistenciaPort;
import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Repository
public class AuditoriaJpaAdapter implements AuditoriaPersistenciaPort {
    private final AuditoriaSeguridadJpaRepository registros;

    public AuditoriaJpaAdapter(AuditoriaSeguridadJpaRepository registros) {
        this.registros = registros;
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public long guardar(RegistroAuditoria registro) {
        return registros.saveAndFlush(AuditoriaPersistenceMapper.aEntidad(registro)).getId();
    }

    @Override public boolean existeRegistro(UUID registroId) {
        return registros.existsByRegistroId(registroId);
    }
}
