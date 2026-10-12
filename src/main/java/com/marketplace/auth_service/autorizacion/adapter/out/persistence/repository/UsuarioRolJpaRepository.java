package com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.UsuarioRolJpaEntity;
import com.marketplace.auth_service.autorizacion.domain.model.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Solo lo usa el adaptador de autorización: ningún otro dueño consulta {@code usuario_rol}. */
public interface UsuarioRolJpaRepository extends JpaRepository<UsuarioRolJpaEntity, UsuarioRolJpaEntity.Id> {

    @Query("SELECT ur.id.rol FROM UsuarioRolJpaEntity ur WHERE ur.id.usuarioId = :usuarioId")
    List<Rol> rolesDe(@Param("usuarioId") UUID usuarioId);

    /**
     * Unión sin duplicados de los permisos de cada rol (RF-11.4), en una sola
     * consulta sobre las PK e índices de {@code usuario_rol} y {@code rol_permiso}.
     */
    @Query(value = """
            SELECT DISTINCT rp.permiso_codigo
            FROM usuario_rol ur
            JOIN rol_permiso rp ON rp.rol_codigo = ur.rol_codigo
            WHERE ur.usuario_id = :usuarioId
            """, nativeQuery = true)
    List<String> permisosDe(@Param("usuarioId") UUID usuarioId);

    @Query("SELECT ur.id.usuarioId FROM UsuarioRolJpaEntity ur WHERE ur.id.rol = :rol")
    List<UUID> usuariosConRol(@Param("rol") Rol rol);
}
