package com.marketplace.auth_service.credencial.adapter.out.persistence.entity;

import com.marketplace.auth_service.credencial.domain.model.AlgoritmoHash;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "credencial")
public class CredencialJpaEntity {
    @Id
    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "hash_contrasena", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "ultimo_cambio", nullable = false)
    private OffsetDateTime actualizadoEn;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CredencialJpaEntity() {
    }

    public CredencialJpaEntity(
            UUID usuarioId,
            String passwordHash,
            OffsetDateTime actualizadoEn) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
    }

    public void reemplazarHash(String passwordHash, OffsetDateTime actualizadoEn) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
    }

    public UUID getId() {
        return usuarioId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AlgoritmoHash getAlgoritmo() {
        return AlgoritmoHash.desdeHashCodificado(passwordHash);
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "CredencialJpaEntity[usuarioId=" + usuarioId + ", REDACTADO]";
    }
}
