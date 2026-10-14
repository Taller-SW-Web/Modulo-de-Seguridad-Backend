package com.marketplace.auth_service.usuario.adapter.out.persistence.entity;

import com.marketplace.auth_service.usuario.domain.model.CodigoRol;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class UsuarioJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "nombres", nullable = false, length = 100)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 100)
    private String apellidos;

    @Column(name = "correo", nullable = false, length = 254, unique = true)
    private String correo;

    @Column(name = "correo_normalizado", nullable = false, length = 254, unique = true)
    private String correoNormalizado;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_algoritmo", nullable = false, length = 50)
    private String passwordAlgoritmo;

    @Column(name = "celular", length = 30)
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

    @Column(name = "fecha_nacimiento")
    private java.time.LocalDate fechaNacimiento;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo_documento")
    private CodigoRol tipoDocumento;

    @Column(name = "documento_cifrado")
    private String documentoCifrado;

    @Column(name = "documento_hmac", length = 64)
    private String documentoHmac;

    @Column(name = "codigo_vendedor", length = 50)
    private String codigoVendedor;

    @Column(name = "tienda", length = 100)
    private String tienda;

    @Column(name = "fecha_ingreso")
    private java.time.LocalDate fechaIngreso;

    @Column(name = "bloqueos_seguidos", nullable = false)
    private int bloqueosSeguidos;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "acepta_terminos_version", length = 50)
    private String aceptaTerminosVersion;

    @Column(name = "acepta_terminos_en")
    private OffsetDateTime aceptaTerminosEn;

    @Column(name = "canal_origen", length = 30)
    private String canalOrigen;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_rol", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "rol", nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Set<CodigoRol> roles = new HashSet<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getCorreoNormalizado() { return correoNormalizado; }
    public void setCorreoNormalizado(String correoNormalizado) { this.correoNormalizado = correoNormalizado; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPasswordAlgoritmo() { return passwordAlgoritmo; }
    public void setPasswordAlgoritmo(String passwordAlgoritmo) { this.passwordAlgoritmo = passwordAlgoritmo; }
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
    public java.time.LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(java.time.LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
    public CodigoRol getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(CodigoRol tipoDocumento) { this.tipoDocumento = tipoDocumento; }
    public String getDocumentoCifrado() { return documentoCifrado; }
    public void setDocumentoCifrado(String documentoCifrado) { this.documentoCifrado = documentoCifrado; }
    public String getDocumentoHmac() { return documentoHmac; }
    public void setDocumentoHmac(String documentoHmac) { this.documentoHmac = documentoHmac; }
    public String getCodigoVendedor() { return codigoVendedor; }
    public void setCodigoVendedor(String codigoVendedor) { this.codigoVendedor = codigoVendedor; }
    public String getTienda() { return tienda; }
    public void setTienda(String tienda) { this.tienda = tienda; }
    public java.time.LocalDate getFechaIngreso() { return fechaIngreso; }
    public void setFechaIngreso(java.time.LocalDate fechaIngreso) { this.fechaIngreso = fechaIngreso; }
    public int getBloqueosSeguidos() { return bloqueosSeguidos; }
    public void setBloqueosSeguidos(int bloqueosSeguidos) { this.bloqueosSeguidos = bloqueosSeguidos; }
    public int getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(int intentosFallidos) { this.intentosFallidos = intentosFallidos; }
    public String getAceptaTerminosVersion() { return aceptaTerminosVersion; }
    public void setAceptaTerminosVersion(String aceptaTerminosVersion) { this.aceptaTerminosVersion = aceptaTerminosVersion; }
    public OffsetDateTime getAceptaTerminosEn() { return aceptaTerminosEn; }
    public void setAceptaTerminosEn(OffsetDateTime aceptaTerminosEn) { this.aceptaTerminosEn = aceptaTerminosEn; }
    public String getCanalOrigen() { return canalOrigen; }
    public void setCanalOrigen(String canalOrigen) { this.canalOrigen = canalOrigen; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Set<CodigoRol> getRoles() { return roles; }
    public void setRoles(Set<CodigoRol> roles) { this.roles = roles; }
}