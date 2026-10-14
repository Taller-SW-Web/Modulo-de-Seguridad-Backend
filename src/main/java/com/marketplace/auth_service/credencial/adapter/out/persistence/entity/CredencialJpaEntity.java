package com.marketplace.auth_service.credencial.adapter.out.persistence.entity;

import com.marketplace.auth_service.credencial.domain.model.AlgoritmoHash;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, unique = true)
    private UUID usuarioId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "algoritmo", nullable = false, length = 50)
    private AlgoritmoHash algoritmo;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CredencialJpaEntity() {
    }

    public CredencialJpaEntity(
            UUID id,
            UUID usuarioId,
            String passwordHash,
            AlgoritmoHash algoritmo,
            OffsetDateTime actualizadoEn) {
        this.id = Objects.requireNonNull(id, "id");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.algoritmo = Objects.requireNonNull(algoritmo, "algoritmo");
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
    }

    public void reemplazarHash(String passwordHash, AlgoritmoHash algoritmo, OffsetDateTime actualizadoEn) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.algoritmo = Objects.requireNonNull(algoritmo, "algoritmo");
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AlgoritmoHash getAlgoritmo() {
        return algoritmo;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "CredencialJpaEntity[id=" + id + ", usuarioId=" + usuarioId + ", REDACTADO]";
    }
}
