package com.marketplace.auth_service.usuario.adapter.out.persistence.entity;

import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "usuario_estado_efectivo")
public class UsuarioEstadoEfectivoJpaEntity {

    @Id
    @Column(name = "usuario_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado", nullable = false, columnDefinition = "estado_cuenta_efectivo")
    private EstadoCuenta estado;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public EstadoCuenta getEstado() { return estado; }
    public void setEstado(EstadoCuenta estado) { this.estado = estado; }
}
