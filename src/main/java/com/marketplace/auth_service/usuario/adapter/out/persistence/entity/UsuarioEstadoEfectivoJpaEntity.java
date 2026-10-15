package com.marketplace.auth_service.usuario.adapter.out.persistence.entity;

import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "usuario_estado_efectivo")
@Subselect("SELECT u.*, CASE WHEN u.estado = 'BLOQUEADO' AND b.hasta IS NOT NULL AND b.hasta < NOW() AT TIME ZONE 'UTC' THEN 'ACTIVO'::estado_cuenta ELSE u.estado END AS estado_efectivo FROM usuario u LEFT JOIN bloqueo b ON b.usuario_id = u.id AND b.tipo = 'AUTOMATICO' AND b.hasta IS NOT NULL")
@Synchronize({"usuario", "bloqueo"})
public class UsuarioEstadoEfectivoJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "nombres", nullable = false, length = 100)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 100)
    private String apellidos;

    @Column(name = "correo", nullable = false, length = 254)
    private String correo;

    @Column(name = "celular", length = 12)
    private String celular;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado", nullable = false)
    private EstadoCuenta estado;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado_efectivo", nullable = false, insertable = false, updatable = false)
    private EstadoCuenta estadoEfectivo;

    @Column(name = "correo_verificado", nullable = false)
    private boolean correoVerificado;

    @Column(name = "mfa_habilitado", nullable = false)
    private boolean mfaHabilitado;

    @Column(name = "bloqueos_seguidos", nullable = false)
    private int bloqueosSeguidos;

    @Column(name = "tipo_documento", length = 20)
    private String tipoDocumento;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;

    public UUID getId() { return id; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public String getCorreo() { return correo; }
    public String getCelular() { return celular; }
    public EstadoCuenta getEstado() { return estado; }
    public EstadoCuenta getEstadoEfectivo() { return estadoEfectivo; }
    public boolean isCorreoVerificado() { return correoVerificado; }
    public boolean isMfaHabilitado() { return mfaHabilitado; }
    public int getBloqueosSeguidos() { return bloqueosSeguidos; }
    public String getTipoDocumento() { return tipoDocumento; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }
}