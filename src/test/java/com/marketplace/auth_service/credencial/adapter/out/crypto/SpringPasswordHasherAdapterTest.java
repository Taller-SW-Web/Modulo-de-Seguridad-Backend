package com.marketplace.auth_service.credencial.adapter.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.credencial.domain.model.AlgoritmoHash;
import com.marketplace.auth_service.credencial.domain.model.HashContrasena;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SpringPasswordHasherAdapterTest {
    private final SpringPasswordHasherAdapter hasher = new SpringPasswordHasherAdapter();

    @Test
    void generatesArgon2idWithRequiredParametersAndPreservesSecretExactly() {
        String secret = "  ExactSecret7!  ";
        HashContrasena hash = hasher.generar(secret);

        assertThat(hash.algoritmo()).isEqualTo(AlgoritmoHash.ARGON2ID);
        assertThat(hash.valor()).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(hash.valor()).isNotEqualTo(secret);
        assertThat(hasher.verificar(secret, hash)).isTrue();
        assertThat(hasher.verificar(secret.trim(), hash)).isFalse();
    }

    @Test
    void verifiesOnlyBcryptHashesWithCostAtLeastTwelve() {
        String secret = "ExistingSecret7!";
        BCryptPasswordEncoder strongEncoder = new BCryptPasswordEncoder(12);
        BCryptPasswordEncoder weakEncoder = new BCryptPasswordEncoder(11);
        HashContrasena strongHash =
                new HashContrasena(strongEncoder.encode(secret), AlgoritmoHash.BCRYPT);
        HashContrasena weakHash =
                new HashContrasena(weakEncoder.encode(secret), AlgoritmoHash.BCRYPT);

        assertThat(hasher.verificar(secret, strongHash)).isTrue();
        assertThat(hasher.verificar(secret, weakHash)).isFalse();
        assertThat(hasher.verificar("wrong", strongHash)).isFalse();
    }

    @Test
    void rejectsMalformedHashes() {
        assertThat(hasher.verificar(
                "secret",
                new HashContrasena("not-a-bcrypt-hash", AlgoritmoHash.BCRYPT))).isFalse();
        assertThat(hasher.verificar(
                "secret",
                new HashContrasena("$argon2i$invalid", AlgoritmoHash.ARGON2ID))).isFalse();
    }

    @Test
    void doesNotLeakHashesInToString() {
        HashContrasena hash = hasher.generar("SensitiveSecret7!");

        assertThat(hash.toString()).doesNotContain(hash.valor());
    }
}
