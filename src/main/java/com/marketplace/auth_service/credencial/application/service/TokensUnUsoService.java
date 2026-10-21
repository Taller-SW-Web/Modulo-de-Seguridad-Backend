package com.marketplace.auth_service.credencial.application.service;

import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.dto.TokenEmitido;
import com.marketplace.auth_service.credencial.application.dto.TokenUnUsoSnapshot;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.credencial.application.port.out.TokenUnUsoRepositoryPort;
import com.marketplace.auth_service.credencial.domain.model.PropositoTokenUnUso;
import com.marketplace.auth_service.credencial.domain.model.TokenUnUso;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Núcleo puro de emisión, localización, consumo e invalidación. */
public final class TokensUnUsoService implements TokensUnUsoPort {
    public static final Duration TTL_VERIFICACION = Duration.ofHours(24);
    public static final Duration TTL_RECUPERACION_MAX = Duration.ofMinutes(30);
    public static final Duration TTL_DESBLOQUEO = Duration.ofMinutes(30);
    private final TokenUnUsoRepositoryPort repositorio;
    private final Clock reloj;
    private final SecureRandom aleatorio;
    private static final Map<PropositoToken, PropositoTokenUnUso> MAPEO = new EnumMap<>(PropositoToken.class);
    static {
        for (PropositoToken p : PropositoToken.values()) {
            MAPEO.put(p, p == PropositoToken.DESBLOQUEO_AUTOMATICO
                    ? PropositoTokenUnUso.DESBLOQUEO_AUTOMATICO : PropositoTokenUnUso.valueOf(p.name()));
        }
    }

    public TokensUnUsoService(TokenUnUsoRepositoryPort repositorio, Clock reloj, SecureRandom aleatorio) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.reloj = Objects.requireNonNull(reloj);
        this.aleatorio = Objects.requireNonNull(aleatorio);
    }

    @Override public Optional<TokenUnUsoSnapshot> localizar(String hash, PropositoToken proposito) {
        Objects.requireNonNull(hash); Objects.requireNonNull(proposito);
        return repositorio.buscar(hash, mapeo(proposito)).map(TokensUnUsoService::snapshot);
    }

    @Override public TokenEmitido emitir(UUID usuarioId, PropositoToken proposito, Duration ttl) {
        if (proposito == PropositoToken.DESBLOQUEO_AUTOMATICO)
            throw new IllegalArgumentException("DESBLOQUEO_AUTOMATICO requiere la revisión UUID del bloqueo");
        return emitirConDatos(usuarioId, proposito, null, null, ttl);
    }

    @Override public TokenEmitido emitir(UUID usuarioId, PropositoToken proposito, UUID contextoId, Duration ttl) {
        return emitirConDatos(usuarioId, proposito, contextoId, null, ttl);
    }

    @Override public TokenEmitido emitirCambioCorreo(UUID usuarioId, String destino) {
        return emitirConDatos(usuarioId, PropositoToken.CAMBIO_CORREO, null,
                Objects.requireNonNull(destino, "destino"), TTL_VERIFICACION);
    }

    private TokenEmitido emitirConDatos(UUID usuarioId, PropositoToken proposito, UUID contextoId,
                                        String destino, Duration ttl) {
        Objects.requireNonNull(usuarioId); Objects.requireNonNull(proposito); Objects.requireNonNull(ttl);
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("El TTL debe ser positivo");
        if (proposito == PropositoToken.VERIFICAR_CORREO && !TTL_VERIFICACION.equals(ttl))
            throw new IllegalArgumentException("VERIFICAR_CORREO vence a las 24 horas");
        if (proposito == PropositoToken.RECUPERACION && ttl.compareTo(TTL_RECUPERACION_MAX) > 0)
            throw new IllegalArgumentException("RECUPERACION no puede exceder 30 minutos");
        if (proposito == PropositoToken.DESBLOQUEO_AUTOMATICO) {
            Objects.requireNonNull(contextoId, "contextoId");
            if (!TTL_DESBLOQUEO.equals(ttl)) throw new IllegalArgumentException("El enlace de desbloqueo vence a los 30 minutos");
        }
        if (proposito == PropositoToken.CAMBIO_CORREO) {
            if (destino == null || destino.isBlank() || destino.length() > 254)
                throw new IllegalArgumentException("CAMBIO_CORREO requiere un destino válido");
            if (!TTL_VERIFICACION.equals(ttl)) throw new IllegalArgumentException("CAMBIO_CORREO vence a las 24 horas");
        } else if (destino != null) {
            throw new IllegalArgumentException("El destino solo aplica a CAMBIO_CORREO");
        }

        // PostgreSQL timestamptz stores microsecond precision; align persisted and returned times.
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String claro = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = TokenUnUsoHashing.sha256Hex(claro);
        Instant expira = ahora.plus(ttl).truncatedTo(ChronoUnit.MICROS);
        UUID id = UUID.randomUUID();
        PropositoTokenUnUso dominio = mapeo(proposito);

        // La invalidación y la inserción deben quedar en el mismo commit del productor.
        repositorio.invalidarPrevios(usuarioId, dominio, ahora);
        repositorio.guardar(new TokenUnUso(id, usuarioId, dominio, hash, contextoId, destino, expira, ahora, false));
        return new TokenEmitido(id, claro, expira);
    }

    @Override public boolean consumir(UUID id, UUID usuarioId, PropositoToken proposito, Instant ahora) {
        return consumir(id, usuarioId, proposito, null, ahora);
    }

    @Override public boolean consumir(UUID id, UUID usuarioId, PropositoToken proposito, UUID contextoId, Instant ahora) {
        Objects.requireNonNull(id); Objects.requireNonNull(usuarioId); Objects.requireNonNull(proposito); Objects.requireNonNull(ahora);
        Optional<TokenUnUso> encontrado = repositorio.bloquear(id);
        if (encontrado.isEmpty()) return false;
        TokenUnUso token = encontrado.get();
        if (!token.usuarioId().equals(usuarioId) || token.proposito() != mapeo(proposito)
                || !Objects.equals(token.contextoId(), contextoId) || !token.vigenteEn(ahora))
            return false;
        return repositorio.marcarUsado(id);
    }

    @Override public void invalidarPrevios(UUID usuarioId, PropositoToken proposito) {
        repositorio.invalidarPrevios(Objects.requireNonNull(usuarioId), mapeo(Objects.requireNonNull(proposito)), reloj.instant());
    }

    private static PropositoTokenUnUso mapeo(PropositoToken p) { return MAPEO.get(p); }
    private static TokenUnUsoSnapshot snapshot(TokenUnUso t) {
        return new TokenUnUsoSnapshot(t.id(), t.usuarioId(), PropositoToken.valueOf(
                t.proposito() == PropositoTokenUnUso.DESBLOQUEO_AUTOMATICO ? "DESBLOQUEO_AUTOMATICO" : t.proposito().name()),
                t.contextoId(), t.destino(), t.expiraEn(), t.creadoEn(), t.usado());
    }
}
