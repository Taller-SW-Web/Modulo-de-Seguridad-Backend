package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ClaimsVerificados;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;
import com.marketplace.auth_service.identidad.application.dto.Descubrimiento;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.port.out.FirmadorJws;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Emite accesos de persona (RF-05.6, RF-17.14): issuer del entorno, 900 s,
 * jti nuevo por emisión. El tiempo sale del {@link Clock} inyectado, truncado a
 * segundos porque {@code iat}/{@code exp} del JWT son segundos enteros.
 *
 * <p>También publica la parte pública (RF-17.1/17.2): el {@code jwks_uri} se
 * deriva del issuer configurado, no de la petición, así que detrás de un proxy
 * sigue apuntando a la URL externa del entorno.
 *
 * <p>Y valida los accesos que recibe el backend (TEC-IDENTIDAD-T002): la
 * criptografía la hace el adaptador; aquí se aplican issuer, tiempos con el
 * mismo {@link Clock} y tolerancia cero ({@code iat} obligatorio y no futuro,
 * {@code nbf} no futuro si viene, {@code exp} posterior a ahora) y el perfil de
 * persona: duración exacta de 900 s, {@code email}, {@code sub}/{@code jti} UUID
 * y roles/permisos como arrays. El token de servicio se separa antes con 403.
 */
public class FirmarAccesoService implements FirmaPort {

    public static final Duration DURACION_ACCESO = Duration.ofSeconds(900);
    public static final String RUTA_JWKS = "/.well-known/jwks.json";
    private static final String TIPO_SERVICIO = "servicio";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+$");

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

    @Override
    public AccesoValidado validar(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException("token ausente");
        }
        ClaimsVerificados claims = firmador.verificar(token);
        if (!issuer.equals(claims.iss())) {
            throw new TokenInvalidoException("issuer distinto del de este entorno");
        }
        Instant ahora = clock.instant();
        if (claims.exp() == null || !claims.exp().isAfter(ahora)) {
            throw new TokenInvalidoException("vencido o sin exp");
        }
        if (claims.iat() == null || claims.iat().isAfter(ahora)) {
            throw new TokenInvalidoException("sin iat o emitido en el futuro");
        }
        if (claims.nbf() != null && claims.nbf().isAfter(ahora)) {
            throw new TokenInvalidoException("todavía no es válido (nbf)");
        }
        if (TIPO_SERVICIO.equals(claims.tipo())) {
            throw new TokenNoAplicableException();
        }
        if (!ContenidoAcceso.TIPO.equals(claims.tipo())) {
            throw new TokenInvalidoException("perfil no es un acceso de persona");
        }
        if (!Duration.between(claims.iat(), claims.exp()).equals(DURACION_ACCESO)) {
            throw new TokenInvalidoException("la duración no es de 900 s");
        }
        if (claims.email() == null || !EMAIL.matcher(claims.email()).matches()) {
            throw new TokenInvalidoException("email ausente o inválido");
        }
        if (claims.roles() == null || claims.permisos() == null) {
            throw new TokenInvalidoException("roles o permisos ausentes o no son arrays");
        }
        return new AccesoValidado(uuid(claims.sub(), "sub"), uuid(claims.jti(), "jti"),
                claims.roles(), claims.permisos(), claims.exp());
    }

    private static UUID uuid(String valor, String claim) {
        if (valor == null) {
            throw new TokenInvalidoException(claim + " ausente");
        }
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new TokenInvalidoException(claim + " no es un UUID");
        }
    }
}
