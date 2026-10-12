package com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity;

import com.marketplace.auth_service.autorizacion.domain.model.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;

/** Tabla {@code rol_permiso}: qué permisos concede cada rol. Fuente de los permisos efectivos. */
@Entity
@Immutable
@Table(name = "rol_permiso")
public class RolPermisoJpaEntity {

    @EmbeddedId
    private Id id;

    protected RolPermisoJpaEntity() {
    }

    public Rol getRol() {
        return id.rol;
    }

    public String getPermisoCodigo() {
        return id.permisoCodigo;
    }

    /** PK compuesta {@code (rol_codigo, permiso_codigo)}. */
    @Embeddable
    public static class Id implements Serializable {

        @Enumerated(EnumType.STRING)
        @JdbcTypeCode(SqlTypes.NAMED_ENUM)
        @Column(name = "rol_codigo", columnDefinition = "codigo_rol", nullable = false)
        private Rol rol;

        @Column(name = "permiso_codigo", length = 50, nullable = false)
        private String permisoCodigo;

        protected Id() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Id otro && rol == otro.rol && Objects.equals(permisoCodigo, otro.permisoCodigo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rol, permisoCodigo);
        }
    }
}
