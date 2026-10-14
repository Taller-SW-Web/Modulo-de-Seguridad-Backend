package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;
import com.marketplace.auth_service.identidad.application.dto.Descubrimiento;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.port.out.FirmadorJws;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Emite accesos de persona (RF-05.6, RF-17.14): issuer del entorno, 900 s,
 * jti nuevo por emisión. El tiempo sale del {@link Clock} inyectado, truncado a
 * segundos porque {@code iat}/{@code exp} del JWT son segundos enteros.
 *
 * <p>También publica la parte pública (RF-17.1/17.2): el {@code jwks_uri} se
 * deriva del issuer configurado, no de la petición, así que detrás de un proxy
 * sigue apuntando a la URL externa del entorno.
 */
public class FirmarAccesoService implements FirmaPort {

    public static final Duration DURACION_ACCESO = Duration.ofSeconds(900);
    public static final String RUTA_JWKS = "/.well-known/jwks.json";

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

    @Override
    public List<ClavePublica> jwks() {
        return firmador.clavesPublicas();
    }

    @Override
    public Descubrimiento descubrimiento() {
        return new Descubrimiento(issuer, issuer + RUTA_JWKS, List.of(ClavePublica.ALGORITMO));
    }
}
