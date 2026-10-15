package com.marketplace.auth_service.perfil.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Fila de perfil_vendedor. "tienda" es un nombre, no una referencia. */
@Entity
@Table(name = "perfil_vendedor")
public class PerfilVendedorJpaEntity {

    @Id
    @Column(name = "usuario_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID usuarioId;

    @Column(name = "codigo_vendedor", length = 50)
    private String codigoVendedor;

    @Column(name = "tienda", length = 100)
    private String tienda;

    @Column(name = "fecha_ingreso")
    private LocalDate fechaIngreso;

    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public String getCodigoVendedor() { return codigoVendedor; }
    public void setCodigoVendedor(String codigoVendedor) { this.codigoVendedor = codigoVendedor; }
    public String getTienda() { return tienda; }
    public void setTienda(String tienda) { this.tienda = tienda; }
    public LocalDate getFechaIngreso() { return fechaIngreso; }
    public void setFechaIngreso(LocalDate fechaIngreso) { this.fechaIngreso = fechaIngreso; }
}
