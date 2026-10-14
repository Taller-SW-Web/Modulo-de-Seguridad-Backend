package com.marketplace.auth_service.usuario.adapter.out.persistence.entity;

import com.marketplace.auth_service.usuario.domain.model.CodigoRol;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity
@Table(name = "usuario_rol")
@IdClass(UsuarioRolJpaEntity.PK.class)
public class UsuarioRolJpaEntity {

    @Id
    @Column(name = "usuario_id", nullable = false, columnDefinition = "uuid")
    private UUID usuarioId;

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "rol", nullable = false)
    private CodigoRol rol;

    @Column(name = "asignado_en", nullable = false)
    private java.time.OffsetDateTime asignadoEn;

    @Column(name = "asignado_por", nullable = false, columnDefinition = "uuid")
    private UUID asignadoPor;

    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public CodigoRol getRol() { return rol; }
    public void setRol(CodigoRol rol) { this.rol = rol; }
    public java.time.OffsetDateTime getAsignadoEn() { return asignadoEn; }
    public void setAsignadoEn(java.time.OffsetDateTime asignadoEn) { this.asignadoEn = asignadoEn; }
    public UUID getAsignadoPor() { return asignadoPor; }
    public void setAsignadoPor(UUID asignadoPor) { this.asignadoPor = asignadoPor; }

    public static class PK implements java.io.Serializable {
        public UUID usuarioId;
        public CodigoRol rol;

        public PK() {}
        public PK(UUID usuarioId, CodigoRol rol) {
            this.usuarioId = usuarioId;
            this.rol = rol;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK)) return false;
            PK pk = (PK) o;
            return java.util.Objects.equals(usuarioId, pk.usuarioId) && rol == pk.rol;
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(usuarioId, rol);
        }
    }
}