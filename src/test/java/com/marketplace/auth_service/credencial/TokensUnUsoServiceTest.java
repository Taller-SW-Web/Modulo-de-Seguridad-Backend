package com.marketplace.auth_service.credencial;

import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.service.TokenUnUsoHashing;
import com.marketplace.auth_service.credencial.application.service.TokensUnUsoService;
import com.marketplace.auth_service.credencial.application.port.out.TokenUnUsoRepositoryPort;
import com.marketplace.auth_service.credencial.domain.model.PropositoTokenUnUso;
import com.marketplace.auth_service.credencial.domain.model.TokenUnUso;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokensUnUsoServiceTest {
    private static final Instant AHORA = Instant.parse("2026-10-14T12:00:00Z");

    @Test void emite32BytesSoloGuardaSha256YAsociaRevisionDeBloqueo() {
        RepositorioFalso db = new RepositorioFalso();
        var service = new TokensUnUsoService(db, Clock.fixed(AHORA, ZoneOffset.UTC), new SecureRandom());
        UUID usuario = UUID.randomUUID(), revision = UUID.randomUUID();
        var emitido = service.emitir(usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, revision,
                TokensUnUsoService.TTL_DESBLOQUEO);

        byte[] bytes = Base64.getUrlDecoder().decode(emitido.tokenClaro());
        assertEquals(32, bytes.length);
        assertFalse(emitido.tokenClaro().contains("="));
        assertEquals(AHORA.plus(Duration.ofMinutes(30)), emitido.expiraEn());
        assertFalse(emitido.toString().contains(emitido.tokenClaro()));
        TokenUnUso saved = db.tokens.get(emitido.id());
        assertEquals(TokenUnUsoHashing.sha256Hex(emitido.tokenClaro()), saved.hashToken());
        assertNotEquals(emitido.tokenClaro(), saved.hashToken());
        assertEquals(revision, saved.contextoId());
        assertEquals(PropositoTokenUnUso.DESBLOQUEO_AUTOMATICO, saved.proposito());
        assertEquals(Optional.of(revision), service.localizar(saved.hashToken(), PropositoToken.DESBLOQUEO_AUTOMATICO)
                .map(s -> Optional.ofNullable(s.contextoId())).orElseThrow());
    }

    @Test void requiereContextoYDuracionContractualParaElDesbloqueo() {
        var service = new TokensUnUsoService(new RepositorioFalso(), Clock.fixed(AHORA, ZoneOffset.UTC), new SecureRandom());
        UUID usuario = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> service.emitir(usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, Duration.ofMinutes(30)));
        assertThrows(NullPointerException.class,
                () -> service.emitir(usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, null, Duration.ofMinutes(30)));
        assertThrows(IllegalArgumentException.class,
                () -> service.emitir(usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, UUID.randomUUID(), Duration.ofMinutes(29)));
    }

    @Test void consumeUnaVezYCompruebaUsuarioPropositoYVigencia() {
        RepositorioFalso db = new RepositorioFalso();
        var service = new TokensUnUsoService(db, Clock.fixed(AHORA, ZoneOffset.UTC), new SecureRandom());
        UUID usuario = UUID.randomUUID(), revision = UUID.randomUUID();
        var emitido = service.emitir(usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Duration.ofMinutes(30));
        assertFalse(service.consumir(emitido.id(), UUID.randomUUID(), PropositoToken.DESBLOQUEO_AUTOMATICO, revision, AHORA));
        assertFalse(service.consumir(emitido.id(), usuario, PropositoToken.RECUPERACION, revision, AHORA));
        assertFalse(service.consumir(emitido.id(), usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, UUID.randomUUID(), AHORA));
        assertFalse(service.consumir(emitido.id(), usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, AHORA));
        assertTrue(service.consumir(emitido.id(), usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, AHORA));
        assertFalse(service.consumir(emitido.id(), usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, AHORA));

        var vencido = service.emitir(UUID.randomUUID(), PropositoToken.RECUPERACION, Duration.ofMinutes(1));
        assertFalse(service.consumir(vencido.id(), db.tokens.get(vencido.id()).usuarioId(), PropositoToken.RECUPERACION, null,
                AHORA.plusSeconds(60)));
    }

    @Test void emitirInvalidaTokensVigentesAnterioresDelMismoUsuarioYProposito() {
        RepositorioFalso db = new RepositorioFalso();
        var service = new TokensUnUsoService(db, Clock.fixed(AHORA, ZoneOffset.UTC), new SecureRandom());
        UUID usuario = UUID.randomUUID();
        var primero = service.emitir(usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, UUID.randomUUID(), Duration.ofMinutes(30));
        UUID revisionNueva = UUID.randomUUID();
        var segundo = service.emitir(usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, revisionNueva, Duration.ofMinutes(30));
        assertTrue(db.tokens.get(primero.id()).usado());
        assertFalse(db.tokens.get(segundo.id()).usado());
        assertTrue(service.consumir(segundo.id(), usuario, PropositoToken.DESBLOQUEO_AUTOMATICO, revisionNueva, AHORA));
    }

    private static final class RepositorioFalso implements TokenUnUsoRepositoryPort {
        private final Map<UUID, TokenUnUso> tokens = new LinkedHashMap<>();
        @Override public void guardar(TokenUnUso token) { tokens.put(token.id(), token); }
        @Override public Optional<TokenUnUso> buscar(String hash, PropositoTokenUnUso p) {
            return tokens.values().stream().filter(t -> t.hashToken().equals(hash) && t.proposito() == p).findFirst();
        }
        @Override public Optional<TokenUnUso> bloquear(UUID id) { return Optional.ofNullable(tokens.get(id)); }
        @Override public void invalidarPrevios(UUID usuario, PropositoTokenUnUso p, Instant ahora) {
            tokens.replaceAll((id, t) -> t.usuarioId().equals(usuario) && t.proposito() == p && t.vigenteEn(ahora)
                    ? t.marcarUsado() : t);
        }
        @Override public boolean marcarUsado(UUID id) {
            if (!tokens.containsKey(id) || tokens.get(id).usado()) return false;
            tokens.computeIfPresent(id, (k, t) -> t.marcarUsado());
            return true;
        }
    }
}
