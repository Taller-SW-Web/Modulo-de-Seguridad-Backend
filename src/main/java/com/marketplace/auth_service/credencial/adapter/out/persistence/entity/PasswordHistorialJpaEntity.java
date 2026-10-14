package com.marketplace.auth_service.credencial.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "password_historial")
public class PasswordHistorialJpaEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "credencial_id", nullable = false)
    private UUID credencialId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    protected PasswordHistorialJpaEntity() {
    }

    public PasswordHistorialJpaEntity(UUID id, UUID credencialId, String passwordHash, OffsetDateTime creadoEn) {
        this.id = Objects.requireNonNull(id, "id");
        this.credencialId = Objects.requireNonNull(credencialId, "credencialId");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
    }

    public UUID getId() {
        return id;
    }

    public UUID getCredencialId() {
        return credencialId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    @Override
    public String toString() {
        return "PasswordHistorialJpaEntity[id=" + id + ", credencialId=" + credencialId + ", REDACTADO]";
    }
}
