package com.marketplace.auth_service.identidad.adapter.out.crypto;

import com.marketplace.auth_service.identidad.application.dto.ClaimsVerificados;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;
import com.marketplace.auth_service.identidad.application.port.out.FirmadorJws;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
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

import java.util.List;
import java.util.Objects;

/**
 * Firma RS256 con {@link NimbusJwtEncoder} y la clave persistente del entorno.
 * Cabecera: alg RS256, typ JWT y kid de la clave. Claims: exactamente los del
 * contrato (tokenUsuario), sin {@code aud}.
 *
 * <p>Verifica con {@link NimbusJwtDecoder} y la clave <b>pública local</b>, nunca
 * descargando nuestro propio JWKS por HTTP. El decodificador solo admite RS256
 * y su único validador es el {@code kid}: se quitan las comprobaciones de tiempo
 * por defecto de Spring y de Nimbus (ambas con 60 s de tolerancia) porque la
 * vigencia la decide el caso de uso con su {@code Clock} y tolerancia cero.
 */
public class NimbusFirmadorJws implements FirmadorJws {

    private final JwtEncoder encoder;
    private final String kid;
    private final ClavePublica publica;
    private final NimbusJwtDecoder decoder;

    public NimbusFirmadorJws(ClaveFirmaRsa clave) {
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(clave.jwkPrivada())));
        this.kid = clave.kid();
        RSAKey jwk = clave.jwkPublica();
        this.publica = new ClavePublica(jwk.getKeyID(), jwk.getModulus().toString(), jwk.getPublicExponent().toString());
        try {
            this.decoder = NimbusJwtDecoder.withPublicKey(jwk.toRSAPublicKey())
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    // Nimbus revisa exp con 60 s de tolerancia por su cuenta; se desactiva
                    // para que la vigencia la decida solo el caso de uso (tolerancia cero).
                    .jwtProcessorCustomizer(procesador -> procesador.setJWTClaimsSetVerifier((claims, contexto) -> { }))
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("no se pudo preparar la verificación con la clave pública", e);
        }
        this.decoder.setJwtValidator(kidPropio(kid));
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

    /** Una sola clave en H3; la rotación (clave nueva y anterior a la vez) es posterior. */
    @Override
    public List<ClavePublica> clavesPublicas() {
        return List.of(publica);
    }

    @Override
    public ClaimsVerificados verificar(String token) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException e) {
            throw new TokenInvalidoException("firma, formato, algoritmo o kid inválidos");
        }
        return new ClaimsVerificados(
                texto(jwt.getClaims().get("iss")),
                texto(jwt.getClaims().get("tipo")),
                texto(jwt.getClaims().get("sub")),
                texto(jwt.getClaims().get("jti")),
                listaDeTextos(jwt.getClaims().get("roles")),
                listaDeTextos(jwt.getClaims().get("permisos")),
                jwt.getExpiresAt());
    }

    /** Rechaza tokens sin {@code kid} o con uno que no es el de nuestra clave. */
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
