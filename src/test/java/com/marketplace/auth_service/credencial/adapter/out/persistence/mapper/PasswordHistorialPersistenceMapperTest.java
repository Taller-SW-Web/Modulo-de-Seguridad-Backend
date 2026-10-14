package com.marketplace.auth_service.credencial.adapter.out.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.PasswordHistorialJpaEntity;
import com.marketplace.auth_service.credencial.domain.model.AlgoritmoHash;
import com.marketplace.auth_service.credencial.domain.model.HashContrasena;
import com.marketplace.auth_service.credencial.domain.model.PasswordHistorial;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordHistorialPersistenceMapperTest {
    @Test
    void mapsMixedHashAlgorithmsAndPreservesStableHistoryOrder() {
        UUID credentialId = UUID.randomUUID();
        Instant firstAt = Instant.parse("2026-10-01T00:00:00Z");
        UUID olderId = new UUID(0, 1);
        UUID newerId = new UUID(0, 2);
        PasswordHistorial model = new PasswordHistorial(List.of(
                new PasswordHistorial.Entrada(
                        newerId,
                        new HashContrasena("$2b$12$" + "x".repeat(53), AlgoritmoHash.BCRYPT),
                        firstAt.plusSeconds(1)),
                new PasswordHistorial.Entrada(
                        olderId,
                        new HashContrasena("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA", AlgoritmoHash.ARGON2ID),
                        firstAt)));

        List<PasswordHistorialJpaEntity> rows = PasswordHistorialPersistenceMapper.aEntidades(credentialId, model);
        PasswordHistorial roundTrip = PasswordHistorialPersistenceMapper.aDominio(rows.reversed());

        assertThat(rows).allSatisfy(row -> assertThat(row.getCredencialId()).isEqualTo(credentialId));
        assertThat(roundTrip.entradas()).extracting(PasswordHistorial.Entrada::id)
                .containsExactly(olderId, newerId);
        assertThat(roundTrip.entradas().get(0).hash().algoritmo()).isEqualTo(AlgoritmoHash.ARGON2ID);
        assertThat(roundTrip.entradas().get(1).hash().algoritmo()).isEqualTo(AlgoritmoHash.BCRYPT);
        assertThat(rows.get(0).getCreadoEn().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(rows.get(0).toString()).doesNotContain(rows.get(0).getPasswordHash());
    }
}
