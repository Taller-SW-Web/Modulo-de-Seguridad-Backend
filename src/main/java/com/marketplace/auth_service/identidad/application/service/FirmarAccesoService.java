package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.port.out.FirmadorJws;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Emite accesos de persona (RF-05.6, RF-17.14): issuer del entorno, 900 s,
 * jti nuevo por emisión. El tiempo sale del {@link Clock} inyectado, truncado a
 * segundos porque {@code iat}/{@code exp} del JWT son segundos enteros.
 */
public class FirmarAccesoService implements FirmaPort {

    public static final Duration DURACION_ACCESO = Duration.ofSeconds(900);

    private final FirmadorJws firmador;
    private final String issuer;
    private final Clock clock;

    public FirmarAccesoService(FirmadorJws firmador, String issuer, Clock clock) {
        this.firmador = Objects.requireNonNull(firmador, "firmador");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AccesoFirmado firmar(ClaimsAcceso claims) {
        Objects.requireNonNull(claims, "claims");
        Instant iat = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant exp = iat.plus(DURACION_ACCESO);
        UUID jti = UUID.randomUUID();

        String token = firmador.firmar(new ContenidoAcceso(issuer, claims.usuarioId(), claims.email(),
                claims.roles(), claims.permisos(), iat, exp, jti));
        return new AccesoFirmado(token, jti, iat, exp);
    }
}
