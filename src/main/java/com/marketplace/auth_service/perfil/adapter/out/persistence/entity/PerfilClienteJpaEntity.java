package com.marketplace.auth_service.perfil.adapter.out.persistence.entity;

import com.marketplace.auth_service.perfil.domain.model.TipoDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.util.UUID;

/** Fila de perfil_cliente. El documento se guarda tal cual: cifrado, con su key_id y HMAC (sin descifrar aquí). */
@Entity
@Table(name = "perfil_cliente")
public class PerfilClienteJpaEntity {

    @Id
    @Column(name = "usuario_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo_documento", columnDefinition = "tipo_documento")
    private TipoDocumento tipoDocumento;

    @Column(name = "documento_cifrado", columnDefinition = "text")
    private String documentoCifrado;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "documento_key_id", length = 50)
    private String documentoKeyId;

    @Column(name = "documento_hmac", length = 64)
    private String documentoHmac;

    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public TipoDocumento getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(TipoDocumento tipoDocumento) { this.tipoDocumento = tipoDocumento; }
    public String getDocumentoCifrado() { return documentoCifrado; }
    public void setDocumentoCifrado(String documentoCifrado) { this.documentoCifrado = documentoCifrado; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
    public String getDocumentoKeyId() { return documentoKeyId; }
    public void setDocumentoKeyId(String documentoKeyId) { this.documentoKeyId = documentoKeyId; }
    public String getDocumentoHmac() { return documentoHmac; }
    public void setDocumentoHmac(String documentoHmac) { this.documentoHmac = documentoHmac; }

    @Override
    public String toString() {
        return "PerfilClienteJpaEntity[usuarioId=" + usuarioId + ", REDACTADO]";
    }
}
