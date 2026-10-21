package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ClaimsServicio;
import com.marketplace.auth_service.identidad.application.dto.ClaimsVerificados;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;
import com.marketplace.auth_service.identidad.application.dto.ContenidoServicio;
import com.marketplace.auth_service.identidad.application.dto.Descubrimiento;
import com.marketplace.auth_service.identidad.application.dto.InspeccionAcceso;
import com.marketplace.auth_service.identidad.application.dto.ServicioValidado;
import com.marketplace.auth_service.identidad.application.dto.TokenServicioFirmado;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.port.out.FirmadorJws;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;
import com.marketplace.auth_service.shared.domain.ScopeServicio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
 *
 * <p>Desde FEAT-05 emite y valida además tokens de servicio (SPEC-17): 3600 s,
 * {@code sub} = client_id, {@code aud} y {@code scope}; y los anuncia en el
 * descubrimiento junto con la introspección.
 */
public class FirmarAccesoService implements FirmaPort {

    public static final Duration DURACION_ACCESO = Duration.ofSeconds(900);
    public static final Duration DURACION_SERVICIO = Duration.ofSeconds(3600);
    public static final String RUTA_JWKS = "/.well-known/jwks.json";
    public static final String RUTA_TOKEN = "/token";
    public static final String RUTA_INTROSPECCION = "/introspeccion";
    private static final String TIPO_SERVICIO = ContenidoServicio.TIPO;
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
    public TokenServicioFirmado firmarServicio(ClaimsServicio claims) {
        Objects.requireNonNull(claims, "claims");
        Instant iat = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant exp = iat.plus(DURACION_SERVICIO);
        UUID jti = UUID.randomUUID();
        String token = firmador.firmarServicio(new ContenidoServicio(issuer, claims.clientId(),
                claims.audiencias(), claims.scopes(), iat, exp, jti));
        return new TokenServicioFirmado(token, jti, iat, exp);
    }

    @Override
    public List<ClavePublica> jwks() {
        return firmador.clavesPublicas();
    }

    @Override
    public Descubrimiento descubrimiento() {
        return new Descubrimiento(issuer, issuer + RUTA_JWKS, List.of(ClavePublica.ALGORITMO),
                issuer + RUTA_TOKEN, issuer + RUTA_INTROSPECCION, List.of("client_credentials"),
                Arrays.stream(ScopeServicio.values()).map(ScopeServicio::codigo).toList());
    }

    @Override
    public InspeccionAcceso inspeccionar(String token) {
        ClaimsVerificados claims;
        try {
            claims = verificarComun(token);
        } catch (TokenVencido vencido) {
            return new InspeccionAcceso.Vencido();
        } catch (TokenInvalidoException invalido) {
            return new InspeccionAcceso.Invalido();
        }
        try {
            return new InspeccionAcceso.Valido(perfilDePersona(claims));
        } catch (TokenInvalidoException | TokenNoAplicableException noEsPersona) {
            return new InspeccionAcceso.Invalido();
        }
    }

    @Override
    public ServicioValidado validarServicio(String token) {
        ClaimsVerificados claims = verificarComun(token);
        if (ContenidoAcceso.TIPO.equals(claims.tipo())) {
            throw new TokenNoAplicableException();
        }
        if (!TIPO_SERVICIO.equals(claims.tipo())) {
            throw new TokenInvalidoException("perfil no es un token de servicio");
        }
        if (!Duration.between(claims.iat(), claims.exp()).equals(DURACION_SERVICIO)) {
            throw new TokenInvalidoException("la duración no es de 3600 s");
        }
        if (claims.sub() == null || claims.sub().isBlank()) {
            throw new TokenInvalidoException("sub ausente");
        }
        if (claims.aud() == null || !claims.aud().contains(ScopeServicio.AUDIENCIA)) {
            throw new TokenInvalidoException("no va dirigido a esta API");
        }
        if (claims.scope() == null) {
            throw new TokenInvalidoException("scope ausente");
        }
        Set<String> scopes = Arrays.stream(claims.scope().split(" "))
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
        return new ServicioValidado(claims.sub(), uuid(claims.jti(), "jti"), scopes, claims.exp());
    }

    @Override
    public AccesoValidado validar(String token) {
        return perfilDePersona(verificarComun(token));
    }

    /** Firma, issuer y tiempos sin tolerancia: lo común a acceso y servicio. */
    private ClaimsVerificados verificarComun(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException("token ausente");
        }
        ClaimsVerificados claims = firmador.verificar(token);
        if (!issuer.equals(claims.iss())) {
            throw new TokenInvalidoException("issuer distinto del de este entorno");
        }
        Instant ahora = clock.instant();
        if (claims.exp() == null) {
            throw new TokenInvalidoException("sin exp");
        }
        if (!claims.exp().isAfter(ahora)) {
            throw new TokenVencido();
        }
        if (claims.iat() == null || claims.iat().isAfter(ahora)) {
            throw new TokenInvalidoException("sin iat o emitido en el futuro");
        }
        if (claims.nbf() != null && claims.nbf().isAfter(ahora)) {
            throw new TokenInvalidoException("todavía no es válido (nbf)");
        }
        return claims;
    }

    /** Perfil de persona sobre claims ya verificados; el de servicio se separa con 403. */
    private AccesoValidado perfilDePersona(ClaimsVerificados claims) {
        if (TIPO_SERVICIO.equals(claims.tipo())) {
            throw new TokenNoAplicableException(claims.sub(), claims.jti());
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

    /** Vencido: sigue siendo un 401 genérico, pero la introspección lo distingue. */
    private static final class TokenVencido extends TokenInvalidoException {
        TokenVencido() {
            super("vencido");
        }
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
