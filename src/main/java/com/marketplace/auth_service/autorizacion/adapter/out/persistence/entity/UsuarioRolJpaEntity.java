package com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity;

import com.marketplace.auth_service.autorizacion.domain.model.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Tabla {@code usuario_rol}: una fila por rol asignado. La PK compuesta impide
 * asignar dos veces el mismo rol. El usuario es un UUID escalar: no hay relación
 * JPA con la entidad de Eva ni cascadas entre dominios (DISENO-PERSISTENCIA §2).
 */
@Entity
@Table(name = "usuario_rol")
public class UsuarioRolJpaEntity {

    @EmbeddedId
    private Id id;

    protected UsuarioRolJpaEntity() {
    }

    public UsuarioRolJpaEntity(UUID usuarioId, Rol rol) {
        this.id = new Id(usuarioId, rol);
    }

    public UUID getUsuarioId() {
        return id.usuarioId;
    }

    public Rol getRol() {
        return id.rol;
    }

    /** PK compuesta {@code (usuario_id, rol_codigo)}. */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "usuario_id", nullable = false)
        private UUID usuarioId;

        @Enumerated(EnumType.STRING)
        @JdbcTypeCode(SqlTypes.NAMED_ENUM)
        @Column(name = "rol_codigo", columnDefinition = "codigo_rol", nullable = false)
        private Rol rol;

        protected Id() {
        }

        public Id(UUID usuarioId, Rol rol) {
            this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
            this.rol = Objects.requireNonNull(rol, "rol");
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Id otro && rol == otro.rol && Objects.equals(usuarioId, otro.usuarioId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(usuarioId, rol);
        }
    }
}
