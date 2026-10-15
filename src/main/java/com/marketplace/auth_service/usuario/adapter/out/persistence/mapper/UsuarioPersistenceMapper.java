package com.marketplace.auth_service.usuario.adapter.out.persistence.mapper;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioEstadoEfectivoJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
                null, // tipoDocumento no está en usuario (está en perfil_cliente)
                enmascararDocumento(null), // documentoHmac está en perfil_cliente
                roles != null ? roles : Set.of()
        );
    }

    public static CuentaSnapshot toSnapshotConEstadoEfectivo(UsuarioJpaEntity usuario, com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioEstadoEfectivoJpaEntity estadoEfectivo, Set<String> roles) {
        if (usuario == null || estadoEfectivo == null) return null;
        return new CuentaSnapshot(
                estadoEfectivo.getId(),
                usuario.getNombres(),
                usuario.getApellidos(),
                usuario.getCorreo(),
                usuario.getCelular(),
                usuario.getEstado().name(),
                estadoEfectivo.getEstado().name(),
                usuario.isCorreoVerificado(),
                usuario.isMfaHabilitado(),
                usuario.getBloqueosSeguidos(),
                null, // tipoDocumento no está en usuario
                null, // documentoEnmascarado
                java.util.Collections.emptySet()
        );
    }

    private static String enmascararDocumento(String documentoHmac) {
        if (documentoHmac == null || documentoHmac.length() < 3) {
            return "***";
        }
        return "*****" + documentoHmac.substring(documentoHmac.length() - 3);
    }
}