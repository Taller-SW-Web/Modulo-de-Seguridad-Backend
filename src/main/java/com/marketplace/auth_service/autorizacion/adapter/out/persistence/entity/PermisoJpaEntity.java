package com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Tabla {@code permiso}: códigos {@code recurso.accion} de este módulo. */
@Entity
@Immutable
@Table(name = "permiso")
public class PermisoJpaEntity {

    @Id
    @Column(name = "codigo", length = 50, nullable = false)
    private String codigo;

    @Column(name = "modulo", length = 50, nullable = false)
    private String modulo;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    protected PermisoJpaEntity() {
    }

    public String getCodigo() {
        return codigo;
    }

    public String getModulo() {
        return modulo;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
