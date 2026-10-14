package com.marketplace.auth_service.identidad.adapter.out.crypto;

import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.ContenidoAcceso;
import com.marketplace.auth_service.identidad.application.port.out.FirmadorJws;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.util.List;

/**
 * Firma RS256 con {@link NimbusJwtEncoder} y la clave persistente del entorno.
 * Cabecera: alg RS256, typ JWT y kid de la clave. Claims: exactamente los del
 * contrato (tokenUsuario), sin {@code aud}.
 */
public class NimbusFirmadorJws implements FirmadorJws {

    private final JwtEncoder encoder;
    private final String kid;
    private final ClavePublica publica;

    public NimbusFirmadorJws(ClaveFirmaRsa clave) {
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(clave.jwkPrivada())));
        this.kid = clave.kid();
        RSAKey jwk = clave.jwkPublica();
        this.publica = new ClavePublica(jwk.getKeyID(), jwk.getModulus().toString(), jwk.getPublicExponent().toString());
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
}
