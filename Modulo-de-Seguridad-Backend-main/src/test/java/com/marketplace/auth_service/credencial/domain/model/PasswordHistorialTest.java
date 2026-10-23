package com.marketplace.auth_service.credencial.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.credencial.adapter.out.crypto.SpringPasswordHasherAdapter;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.credencial.application.service.PasswordHistoryChecker;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordHistorialTest {
    private final SpringPasswordHasherAdapter hasher = new SpringPasswordHasherAdapter();
    private final PasswordHistoryChecker historyChecker = new PasswordHistoryChecker(hasher);

    @Test
    void maintainsFiveMostRecentEntriesInStableOrder() {
        PasswordHistorial historial = new PasswordHistorial(List.of());
        Instant base = Instant.parse("2026-10-01T00:00:00Z");

        for (int index = 0; index < 6; index++) {
            historial = historial.agregar(
                    new UUID(0, index + 1),
                    hasher.generar("Secret" + index + "A!"),
                    base.plusSeconds(index / 2));
        }

        assertThat(historial.entradas()).hasSize(5);
        assertThat(historial.entradas())
                .extracting(PasswordHistorial.Entrada::id)
                .containsExactly(
                        new UUID(0, 2),
                        new UUID(0, 3),
                        new UUID(0, 4),
                        new UUID(0, 5),
                        new UUID(0, 6));
    }

    @Test
    void rejectsEveryPasswordInCompleteFiveEntryHistory() {
        Instant base = Instant.parse("2026-10-01T00:00:00Z");
        List<PasswordHistorial.Entrada> entradas = new ArrayList<>();

        for (int index = 0; index < PasswordHistorial.MAX_ENTRADAS; index++) {
            entradas.add(new PasswordHistorial.Entrada(
                    new UUID(0, index + 1),
                    hasher.generar("History" + index + "A!"),
                    base.plusSeconds(index)));
        }

        PasswordHistorial historial = new PasswordHistorial(entradas);
        Credencial credencial = new Credencial(
                UUID.randomUUID(),
                UUID.randomUUID(),
                entradas.get(entradas.size() - 1).hash(),
                base.plusSeconds(entradas.size() - 1),
                0);

        for (int index = 0; index < PasswordHistorial.MAX_ENTRADAS; index++) {
            assertThat(historyChecker.reutiliza("History" + index + "A!", credencial, historial))
                    .isTrue();
        }
        assertThat(historyChecker.reutiliza("ExpiredSecret7!", credencial, historial)).isFalse();
    }

    @Test
    void comparesAgainstTheCurrentCredentialEvenWhenHistoryIsIncomplete() {
        HashContrasena current = hasher.generar("CurrentSecret7!");
        Credencial credencial = new Credencial(
                UUID.randomUUID(),
                UUID.randomUUID(),
                current,
                Instant.parse("2026-10-01T00:00:00Z"),
                0);

        assertThat(historyChecker.reutiliza(
                "CurrentSecret7!",
                credencial,
                new PasswordHistorial(List.of()))).isTrue();
        assertThat(historyChecker.reutiliza(
                "DifferentSecret8!",
                credencial,
                new PasswordHistorial(List.of()))).isFalse();
    }

    @Test
    void detectsReusedPasswordByVerificationRatherThanHashEquality() {
        HashContrasena previous = hasher.generar("PreviousSecret7!");
        PasswordHistorial historial = new PasswordHistorial(List.of(
                new PasswordHistorial.Entrada(
                        UUID.randomUUID(),
                        previous,
                        Instant.parse("2026-10-01T00:00:00Z"))));

        Credencial current = new Credencial(
                UUID.randomUUID(),
                UUID.randomUUID(),
                hasher.generar("CurrentSecret7!"),
                Instant.parse("2026-10-02T00:00:00Z"),
                1);
        assertThat(historyChecker.reutiliza("PreviousSecret7!", current, historial)).isTrue();
        assertThat(hasher.generar("PreviousSecret7!").valor()).isNotEqualTo(previous.valor());
    }

    @Test
    void doesNotVerifyTheCurrentHashTwiceWhenItIsAlsoInHistory() {
        HashContrasena currentHash = new HashContrasena("encoded-current", AlgoritmoHash.ARGON2ID);
        HashContrasena previousHash = new HashContrasena("encoded-previous", AlgoritmoHash.ARGON2ID);
        Credencial credencial = new Credencial(
                UUID.randomUUID(),
                UUID.randomUUID(),
                currentHash,
                Instant.parse("2026-10-02T00:00:00Z"),
                1);
        PasswordHistorial historial = new PasswordHistorial(List.of(
                new PasswordHistorial.Entrada(
                        UUID.randomUUID(), previousHash, Instant.parse("2026-10-01T00:00:00Z")),
                new PasswordHistorial.Entrada(
                        UUID.randomUUID(), currentHash, Instant.parse("2026-10-02T00:00:00Z"))));
        Map<String, Integer> verificationCounts = new HashMap<>();
        PasswordHasherPort countingHasher = new PasswordHasherPort() {
            @Override
            public HashContrasena generar(CharSequence secreto) {
                throw new UnsupportedOperationException("No se usa en esta prueba");
            }

            @Override
            public boolean verificar(CharSequence secreto, HashContrasena hash) {
                verificationCounts.merge(hash.valor(), 1, Integer::sum);
                return false;
            }
        };

        assertThat(new PasswordHistoryChecker(countingHasher)
                .reutiliza("NewSecret9!", credencial, historial)).isFalse();
        assertThat(verificationCounts)
                .containsEntry("encoded-current", 1)
                .containsEntry("encoded-previous", 1);
    }

    @Test
    void acceptsIncompleteHistoryAndRejectsMoreThanFiveLoadedEntries() {
        assertThat(new PasswordHistorial(List.of()).entradas()).isEmpty();

        List<PasswordHistorial.Entrada> sixEntries = List.of(
                new PasswordHistorial.Entrada(UUID.randomUUID(), hasher.generar("Secret1!"), Instant.EPOCH),
                new PasswordHistorial.Entrada(UUID.randomUUID(), hasher.generar("Secret2!"), Instant.EPOCH.plusSeconds(1)),
                new PasswordHistorial.Entrada(UUID.randomUUID(), hasher.generar("Secret3!"), Instant.EPOCH.plusSeconds(2)),
                new PasswordHistorial.Entrada(UUID.randomUUID(), hasher.generar("Secret4!"), Instant.EPOCH.plusSeconds(3)),
                new PasswordHistorial.Entrada(UUID.randomUUID(), hasher.generar("Secret5!"), Instant.EPOCH.plusSeconds(4)),
                new PasswordHistorial.Entrada(UUID.randomUUID(), hasher.generar("Secret6!"), Instant.EPOCH.plusSeconds(5)));

        assertThatThrownBy(() -> new PasswordHistorial(sixEntries))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
