package com.marketplace.auth_service.direccion.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "direccion")
public class DireccionJpaEntity {
    @Id @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;
    @Column(name = "etiqueta", length = 50)
    private String etiqueta;
    @Column(name = "departamento", nullable = false, length = 100)
    private String departamento;
    @Column(name = "provincia", nullable = false, length = 100)
    private String provincia;
    @Column(name = "distrito", nullable = false, length = 100)
    private String distrito;
    @Column(name = "direccion", nullable = false, length = 255)
    private String direccion;
    @Column(name = "referencia", length = 255)
    private String referencia;
    @Column(name = "es_predeterminada", nullable = false)
    private boolean esPredeterminada;

    protected DireccionJpaEntity() {}
    public DireccionJpaEntity(UUID id, UUID usuarioId, String etiqueta, String departamento,
                              String provincia, String distrito, String direccion,
                              String referencia, boolean esPredeterminada) {
        this.id=id; this.usuarioId=usuarioId; this.etiqueta=etiqueta; this.departamento=departamento;
        this.provincia=provincia; this.distrito=distrito; this.direccion=direccion;
        this.referencia=referencia; this.esPredeterminada=esPredeterminada;
    }
    public UUID getId(){return id;} public UUID getUsuarioId(){return usuarioId;}
    public String getEtiqueta(){return etiqueta;} public String getDepartamento(){return departamento;}
    public String getProvincia(){return provincia;} public String getDistrito(){return distrito;}
    public String getDireccion(){return direccion;} public String getReferencia(){return referencia;}
    public boolean isEsPredeterminada(){return esPredeterminada;}
    public void setEsPredeterminada(boolean value){this.esPredeterminada=value;}
}
