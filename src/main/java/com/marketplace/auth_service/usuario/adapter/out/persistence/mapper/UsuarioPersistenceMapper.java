package com.marketplace.auth_service.usuario.adapter.out.persistence.mapper;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioEstadoEfectivoJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.util.Set;
import java.util.UUID;

public final class UsuarioPersistenceMapper {

    private UsuarioPersistenceMapper() {}

    public static CuentaSnapshot toSnapshot(UsuarioJpaEntity entity, Set<String> roles) {
        if (entity == null) return null;
        return new CuentaSnapshot(
                entity.getId(),
                entity.getNombres(),
                entity.getApellidos(),
                entity.getCorreo(),
                entity.getCelular(),
                entity.getEstado().name(),
                entity.getEstado().name(),
                entity.isCorreoVerificado(),
                entity.isMfaHabilitado(),
                entity.getBloqueosSeguidos(),
                entity.getTipoDocumento(),
                enmascararDocumento(entity.getDocumentoHmac()),
                roles != null ? roles : Set.of()
        );
    }

    public static CuentaSnapshot toSnapshotConEstadoEfectivo(UsuarioEstadoEfectivoJpaEntity entity, Set<String> roles) {
        if (entity == null) return null;
        return new CuentaSnapshot(
                entity.getId(),
                entity.getNombres(),
                entity.getApellidos(),
                entity.getCorreo(),
                entity.getCelular(),
                entity.getEstado().name(),
                entity.getEstadoEfectivo().name(),
                entity.isCorreoVerificado(),
                entity.isMfaHabilitado(),
                entity.getBloqueosSeguidos(),
                entity.getTipoDocumento(),
                enmascararDocumento(entity.getDocumentoHmac()),
                roles != null ? roles : Set.of()
        );
    }

    private static String enmascararDocumento(String documentoHmac) {
        if (documentoHmac == null || documentoHmac.length() < 3) {
            return "***";
        }
        return "*****" + documentoHmac.substring(documentoHmac.length() - 3);
    }
}