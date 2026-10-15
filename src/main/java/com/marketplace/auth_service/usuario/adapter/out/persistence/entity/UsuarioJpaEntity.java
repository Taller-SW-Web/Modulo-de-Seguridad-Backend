package com.marketplace.auth_service.usuario.adapter.out.persistence.entity;

import com.marketplace.auth_service.usuario.domain.model.CanalOrigen;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class UsuarioJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "correo", nullable = false, length = 254)
    private String correo;

    @Column(name = "nombres", nullable = false, length = 100)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 100)
    private String apellidos;

    @Column(name = "celular", length = 12)
    private String celular;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado", nullable = false)
    private EstadoCuenta estado;

    @Column(name = "correo_verificado", nullable = false)
    private boolean correoVerificado;

    @Column(name = "celular_verificado", nullable = false)
    private boolean celularVerificado;

    @Column(name = "mfa_habilitado", nullable = false)
    private boolean mfaHabilitado;

    @Column(name = "acepta_terminos", nullable = false)
    private boolean aceptaTerminos;

    @Column(name = "fecha_aceptacion")
    private OffsetDateTime fechaAceptacion;

    @Column(name = "version_terminos", length = 20)
    private String versionTerminos;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "canal_origen", nullable = false)
    private CanalOrigen canalOrigen;

    @Column(name = "intentos_fallidos_consecutivos", nullable = false)
    private int intentosFallidosConsecutivos;

    @Column(name = "bloqueos_seguidos", nullable = false)
    private int bloqueosSeguidos;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }
    public EstadoCuenta getEstado() { return estado; }
    public void setEstado(EstadoCuenta estado) { this.estado = estado; }
    public boolean isCorreoVerificado() { return correoVerificado; }
    public void setCorreoVerificado(boolean correoVerificado) { this.correoVerificado = correoVerificado; }
    public boolean isCelularVerificado() { return celularVerificado; }
    public void setCelularVerificado(boolean celularVerificado) { this.celularVerificado = celularVerificado; }
    public boolean isMfaHabilitado() { return mfaHabilitado; }
    public void setMfaHabilitado(boolean mfaHabilitado) { this.mfaHabilitado = mfaHabilitado; }
    public boolean isAceptaTerminos() { return aceptaTerminos; }
    public void setAceptaTerminos(boolean aceptaTerminos) { this.aceptaTerminos = aceptaTerminos; }
    public OffsetDateTime getFechaAceptacion() { return fechaAceptacion; }
    public void setFechaAceptacion(OffsetDateTime fechaAceptacion) { this.fechaAceptacion = fechaAceptacion; }
    public String getVersionTerminos() { return versionTerminos; }
    public void setVersionTerminos(String versionTerminos) { this.versionTerminos = versionTerminos; }
    public com.marketplace.auth_service.usuario.domain.model.CanalOrigen getCanalOrigen() { return canalOrigen; }
    public void setCanalOrigen(com.marketplace.auth_service.usuario.domain.model.CanalOrigen canalOrigen) { this.canalOrigen = canalOrigen; }
    public int getIntentosFallidosConsecutivos() { return intentosFallidosConsecutivos; }
    public void setIntentosFallidosConsecutivos(int intentosFallidosConsecutivos) { this.intentosFallidosConsecutivos = intentosFallidosConsecutivos; }
    public int getBloqueosSeguidos() { return bloqueosSeguidos; }
    public void setBloqueosSeguidos(int bloqueosSeguidos) { this.bloqueosSeguidos = bloqueosSeguidos; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }
    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(OffsetDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}