package com.marketplace.auth_service.credencial.adapter.out.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.CredencialJpaEntity;
import com.marketplace.auth_service.credencial.domain.model.AlgoritmoHash;
import com.marketplace.auth_service.credencial.domain.model.Credencial;
import com.marketplace.auth_service.credencial.domain.model.HashContrasena;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredencialPersistenceMapperTest {
    @Test
    void mapsNewCredentialToObservedColumnsWithoutChangingSecretOrHash() {
        String encoded = "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA";
        Instant changedAt = Instant.parse("2026-10-14T16:00:00Z");
        UUID usuarioId = UUID.randomUUID();
        Credencial model = new Credencial(
                usuarioId, usuarioId, new HashContrasena(encoded, AlgoritmoHash.ARGON2ID), changedAt, 0);

        CredencialJpaEntity entity = CredencialPersistenceMapper.aNuevaEntidad(model);

        assertThat(entity.getId()).isEqualTo(model.id());
        assertThat(entity.getUsuarioId()).isEqualTo(model.usuarioId());
        assertThat(entity.getPasswordHash()).isEqualTo(encoded);
        assertThat(entity.getAlgoritmo()).isEqualTo(AlgoritmoHash.ARGON2ID);
        assertThat(entity.getActualizadoEn().toInstant()).isEqualTo(changedAt);
        assertThat(entity.toString()).doesNotContain(encoded);
    }

    @Test
    void refusesToTreatAnExistingRevisionAsANewCredential() {
        UUID usuarioId = UUID.randomUUID();
        Credencial model = new Credencial(
                usuarioId,
                usuarioId,
                new HashContrasena("$2a$12$" + "x".repeat(53), AlgoritmoHash.BCRYPT),
                Instant.EPOCH,
                1);

        assertThatThrownBy(() -> CredencialPersistenceMapper.aNuevaEntidad(model))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesIdentityDifferentFromObservedPrimaryKey() {
        Credencial model = new Credencial(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new HashContrasena("$2a$12$" + "x".repeat(53), AlgoritmoHash.BCRYPT),
                Instant.EPOCH,
                0);

        assertThatThrownBy(() -> CredencialPersistenceMapper.aNuevaEntidad(model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usuario_id");
    }
}
