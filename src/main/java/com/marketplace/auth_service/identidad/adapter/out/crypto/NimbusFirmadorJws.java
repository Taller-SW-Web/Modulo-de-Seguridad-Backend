package com.marketplace.auth_service.identidad.adapter.out.crypto;

import com.marketplace.auth_service.identidad.application.dto.ClaimsVerificados;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;
import com.marketplace.auth_service.identidad.application.dto.ContenidoServicio;
import com.marketplace.auth_service.identidad.application.port.out.FirmadorJws;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Firma RS256 con {@link NimbusJwtEncoder} y la clave persistente del entorno.
 * Cabecera: alg RS256, typ JWT y kid de la clave. Claims: exactamente los del
 * contrato (tokenUsuario sin {@code aud}; tokenServicio con {@code aud} y {@code scope}).
 *
 * <p>Verifica con {@link NimbusJwtDecoder} y la clave <b>pública local</b>, nunca
 * descargando nuestro propio JWKS por HTTP. El decodificador solo admite RS256
 * y su único validador es el {@code kid}: se quitan las comprobaciones de tiempo
 * por defecto de Spring y de Nimbus (ambas con 60 s de tolerancia) porque la
 * vigencia la decide el caso de uso con su {@code Clock} y tolerancia cero.
 *
 * <p>Rotación (RF-17.3, ESC-17.2): firma siempre con la clave actual y, mientras
 * se configure, publica y acepta también la pública anterior. Cada {@code kid}
 * tiene su propio decodificador; un {@code kid} desconocido es inválido.
 */
public class NimbusFirmadorJws implements FirmadorJws {

    private final JwtEncoder encoder;
    private final String kid;
    private final List<ClavePublica> publicas;
    private final Map<String, NimbusJwtDecoder> decoders;

    public NimbusFirmadorJws(ClaveFirmaRsa clave) {
        this(clave, List.of());
    }

    /**
     * @param anteriores públicas de claves retiradas que se siguen publicando y
     *                   aceptando durante la rotación; nunca firman
     */
    public NimbusFirmadorJws(ClaveFirmaRsa clave, List<RSAKey> anteriores) {
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(clave.jwkPrivada())));
        this.kid = clave.kid();
        List<RSAKey> verificables = new ArrayList<>();
        verificables.add(clave.jwkPublica());
        for (RSAKey anterior : anteriores) {
            if (anterior.isPrivate()) {
                throw new IllegalArgumentException("la clave anterior debe ser solo pública");
            }
            if (!anterior.getKeyID().equals(kid)) {
                verificables.add(anterior);
            }
        }
        Map<String, NimbusJwtDecoder> porKid = new LinkedHashMap<>();
        List<ClavePublica> publicadas = new ArrayList<>();
        for (RSAKey jwk : verificables) {
            porKid.put(jwk.getKeyID(), decodificador(jwk));
            publicadas.add(new ClavePublica(jwk.getKeyID(), jwk.getModulus().toString(),
                    jwk.getPublicExponent().toString()));
        }
        this.decoders = Map.copyOf(porKid);
        this.publicas = List.copyOf(publicadas);
    }

    private static NimbusJwtDecoder decodificador(RSAKey jwk) {
        NimbusJwtDecoder decoder;
        try {
            decoder = NimbusJwtDecoder.withPublicKey(jwk.toRSAPublicKey())
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    // Nimbus revisa exp con 60 s de tolerancia por su cuenta; se desactiva
                    // para que la vigencia la decida solo el caso de uso (tolerancia cero).
                    .jwtProcessorCustomizer(procesador -> procesador.setJWTClaimsSetVerifier((claims, contexto) -> { }))
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("no se pudo preparar la verificación con la clave pública", e);
        }
        decoder.setJwtValidator(kidPropio(jwk.getKeyID()));
        return decoder;
    }

    @Override
    public String firmar(ContenidoAcceso contenido) {
        JwsHeader cabecera = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").keyId(kid).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(contenido.issuer())
                .subject(contenido.sub().toString())
                .claim("email", contenido.email())
                .claim("roles", contenido.roles())
                .claim("permisos", contenido.permisos())
                .claim("tipo", ContenidoAcceso.TIPO)
                .issuedAt(contenido.iat())
                .expiresAt(contenido.exp())
                .id(contenido.jti().toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    @Override
    public String firmarServicio(ContenidoServicio contenido) {
        JwsHeader cabecera = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").keyId(kid).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(contenido.issuer())
                .subject(contenido.clientId())
                .audience(contenido.audiencias())
                .claim("tipo", ContenidoServicio.TIPO)
                .claim("scope", String.join(" ", contenido.scopes()))
                .issuedAt(contenido.iat())
                .expiresAt(contenido.exp())
                .id(contenido.jti().toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    /** La clave de firma y, durante una rotación, la anterior (RF-17.3). */
    @Override
    public List<ClavePublica> clavesPublicas() {
        return publicas;
    }

    @Override
    public ClaimsVerificados verificar(String token) {
        String kidPresentado = kidDeCabecera(token);
        NimbusJwtDecoder decoder = kidPresentado == null ? null : decoders.get(kidPresentado);
        if (decoder == null) {
            throw new TokenInvalidoException("kid ausente o desconocido");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException e) {
            throw new TokenInvalidoException("firma, formato, algoritmo o kid inválidos");
        }
        JWTClaimsSet crudos = crudos(token);
        return new ClaimsVerificados(
                texto(jwt.getClaims().get("iss")),
                texto(jwt.getClaims().get("tipo")),
                texto(jwt.getClaims().get("sub")),
                texto(jwt.getClaims().get("jti")),
                crudos.getClaim("email") instanceof String email ? email : null,
                listaDeTextos(jwt.getClaims().get("roles")),
                listaDeTextos(jwt.getClaims().get("permisos")),
                instante(crudos.getIssueTime()),
                instante(crudos.getNotBeforeTime()),
                instante(crudos.getExpirationTime()),
                crudos.getAudience(),
                crudos.getClaim("scope") instanceof String scope ? scope : null);
    }

    /** Lee el kid sin confiar todavía en el token: solo elige con qué clave verificar. */
    private static String kidDeCabecera(String token) {
        if (token == null) {
            return null;
        }
        try {
            return SignedJWT.parse(token).getHeader().getKeyID();
        } catch (ParseException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Claims tal como vienen en el token ya verificado. Los tiempos se toman de
     * aquí y no del {@link Jwt} de Spring, que completa {@code iat} cuando falta.
     */
    private static JWTClaimsSet crudos(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet();
        } catch (ParseException e) {
            throw new TokenInvalidoException("claims ilegibles");
        }
    }

    private static Instant instante(Date fecha) {
        return fecha == null ? null : fecha.toInstant();
    }

    /** Rechaza tokens sin {@code kid} o con uno que no es el de la clave elegida. */
    private static OAuth2TokenValidator<Jwt> kidPropio(String kid) {
        OAuth2Error error = new OAuth2Error("invalid_token", "kid desconocido", null);
        return jwt -> Objects.equals(kid, jwt.getHeaders().get("kid"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    /** El claim debe ser un array de textos; si no lo es, se trata como ausente. */
    private static List<String> listaDeTextos(Object valor) {
        if (!(valor instanceof List<?> lista) || !lista.stream().allMatch(String.class::isInstance)) {
            return null;
        }
        return lista.stream().map(String.class::cast).toList();
    }
}
