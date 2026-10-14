package com.marketplace.auth_service.usuario.adapter.out.persistence.mapper;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioEstadoEfectivoJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.domain.model.CodigoRol;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class UsuarioPersistenceMapper {

    private UsuarioPersistenceMapper() {}

    public static CuentaSnapshot toSnapshot(UsuarioJpaEntity entity, Set<CodigoRol> roles) {
        if (entity == null) return null;
        return new CuentaSnapshot(
                entity.getId(),
                entity.getNombres(),
                entity.getApellidos(),
                entity.getCorreo(),
                entity.getCelular(),
                entity.getEstado(),
                entity.getEstado(),
                entity.isCorreoVerificado(),
                entity.isMfaHabilitado(),
                entity.getBloqueosSeguidos(),
                entity.getTipoDocumento() != null ? entity.getTipoDocumento().name() : null,
                enmascararDocumento(entity.getDocumentoHmac()),
                roles != null ? roles : Set.of()
        );
    }

    public static CuentaSnapshot toSnapshotConEstadoEfectivo(UsuarioEstadoEfectivoJpaEntity entity, Set<CodigoRol> roles) {
        if (entity == null) return null;
        return new CuentaSnapshot(
                entity.getId(),
                entity.getNombres(),
                entity.getApellidos(),
                entity.getCorreo(),
                entity.getCelular(),
                entity.getEstado(),
                entity.getEstadoEfectivo(),
                entity.isCorreoVerificado(),
                entity.isMfaHabilitado(),
                entity.getBloqueosSeguidos(),
                entity.getTipoDocumento() != null ? entity.getTipoDocumento().name() : null,
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

    public static Set<CodigoRol> extraerRoles(UsuarioJpaEntity entity) {
        if (entity == null || entity.getRoles() == null) return Set.of();
        return entity.getRoles();
    }
}