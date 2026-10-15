package com.marketplace.auth_service.credencial.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "token_un_uso")
public class TokenUnUsoJpaEntity {
    @Id @Column(name = "id", nullable = false) private UUID id;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "proposito", columnDefinition = "proposito_token", nullable = false)
    private PropositoTokenPersistido proposito;
    @Column(name = "usuario_id", nullable = false) private UUID usuarioId;
    @Column(name = "hash_token", nullable = false, length = 255) private String hashToken;
    @Column(name = "destino", length = 254) private String destino;
    @Column(name = "expiracion", nullable = false) private OffsetDateTime expiraEn;
    @Column(name = "usado", nullable = false) private boolean usado;
    @Column(name = "creado_en", nullable = false) private OffsetDateTime creadoEn;
    @Column(name = "contexto_id") private UUID contextoId;

    protected TokenUnUsoJpaEntity() { }
    public TokenUnUsoJpaEntity(UUID id, PropositoTokenPersistido proposito, UUID usuarioId, String hashToken,
                               OffsetDateTime expiraEn, boolean usado, OffsetDateTime creadoEn, UUID contextoId) {
        this.id = id; this.proposito = proposito; this.usuarioId = usuarioId; this.hashToken = hashToken;
        this.expiraEn = expiraEn; this.usado = usado; this.creadoEn = creadoEn; this.contextoId = contextoId;
    }
    public UUID getId() { return id; }
    public PropositoTokenPersistido getProposito() { return proposito; }
    public UUID getUsuarioId() { return usuarioId; }
    public String getHashToken() { return hashToken; }
    public OffsetDateTime getExpiraEn() { return expiraEn; }
    public boolean isUsado() { return usado; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public UUID getContextoId() { return contextoId; }
    @Override public String toString() { return "TokenUnUsoJpaEntity[id=" + id + ", usuarioId=" + usuarioId + ", REDACTADO]"; }
}
