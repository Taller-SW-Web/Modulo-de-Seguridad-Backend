package com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity;

import com.marketplace.auth_service.autorizacion.domain.model.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tabla {@code rol}: catálogo cerrado, se siembra y no se edita en runtime. */
@Entity
@Immutable
@Table(name = "rol")
public class RolJpaEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "codigo", columnDefinition = "codigo_rol", nullable = false)
    private Rol codigo;

    @Column(name = "nombre", length = 50, nullable = false)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    protected RolJpaEntity() {
    }

    public Rol getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
