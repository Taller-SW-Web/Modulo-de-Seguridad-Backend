package com.marketplace.auth_service.usuario.adapter.out.persistence.mapper;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioEstadoEfectivoJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import java.util.Objects;

public final class UsuarioPersistenceMapper {
    private UsuarioPersistenceMapper() { }
    public static CuentaSnapshot toSnapshot(UsuarioJpaEntity usuario, UsuarioEstadoEfectivoJpaEntity estado) {
        Objects.requireNonNull(usuario, "usuario");
        Objects.requireNonNull(estado, "estado efectivo");
        if (!usuario.getId().equals(estado.getId())) throw new IllegalArgumentException("Estado de otra cuenta");
        return new CuentaSnapshot(usuario.getId(), usuario.getNombres(), usuario.getApellidos(), usuario.getCorreo(),
                usuario.getCelular(), usuario.getEstado().name(), estado.getEstado().name(),
                usuario.isCorreoVerificado(), usuario.isCelularVerificado(), usuario.isMfaHabilitado(),
                usuario.getBloqueosSeguidos(), usuario.getIntentosFallidosConsecutivos());
    }
}
