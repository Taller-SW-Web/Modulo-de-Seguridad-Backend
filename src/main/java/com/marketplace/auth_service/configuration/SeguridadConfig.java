package com.marketplace.auth_service.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.identidad.adapter.in.security.AccesoAuthenticationManager;
import com.marketplace.auth_service.identidad.adapter.in.security.RespuestasAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Set;

/**
 * Cadena de seguridad HTTP del monolito (ENTORNO-Y-REACT §2, TEC-IDENTIDAD-T002).
 *
 * <ul>
 *   <li><b>Stateless y sin cookies</b>: el acceso viaja solo en la cabecera
 *       {@code Authorization: Bearer}. No hay sesión HTTP, y por eso tampoco CSRF que
 *       proteger. No se acepta el token en la URL ({@code ?access_token=}) ni en cookies.</li>
 *   <li><b>Validación local</b>: {@link AccesoAuthenticationManager} valida con
 *       {@link FirmaPort#validar} y la clave pública del propio proceso, sin
 *       llamar por HTTP a nuestro JWKS.</li>
 *   <li><b>Rutas públicas</b> listadas una a una, nunca con un comodín sobre
 *       {@code /auth/**}. En ellas no se lee el {@code Authorization}: un token
 *       caducado no impide descargar el JWKS. Todo lo demás exige un acceso de
 *       persona válido; los permisos de cada operación los decide su servicio
 *       con {@code AutorizacionPort}.</li>
 *   <li><b>Errores</b> con el formato del contrato ({@link RespuestasAcceso}), nunca HTML.</li>
 * </ul>
 *
 * <p>Cada dueño añade aquí sus rutas públicas H3 cuando las implemente
 * (login, refresh, logout, OTP, desbloqueo, política) y nada más.
 */
@Configuration(proxyBeanMethods = false)
public class SeguridadConfig {

    /** Rutas públicas de identidad (TEC-IDENTIDAD-T003). */
    static final String[] PUBLICAS_IDENTIDAD = {
            "/api/v1/auth/.well-known/jwks.json",
            "/api/v1/auth/.well-known/openid-configuration"
    };

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, FirmaPort firmaPort, ObjectMapper json) throws Exception {
        AuthenticationManager accesos = new AccesoAuthenticationManager(firmaPort);
        RespuestasAcceso respuestas = new RespuestasAcceso(json);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.GET, PUBLICAS_IDENTIDAD).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/desbloquear").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(recurso -> recurso
                        .bearerTokenResolver(soloFueraDeRutasPublicas())
                        .authenticationManagerResolver(peticion -> accesos)
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas));
        return http.build();
    }

    /**
     * Lee el Bearer solo de la cabecera (la implementación por defecto de Spring
     * ya rechaza el parámetro de URL y el cuerpo) y lo ignora en las rutas públicas.
     */
    private static BearerTokenResolver soloFueraDeRutasPublicas() {
        DefaultBearerTokenResolver cabecera = new DefaultBearerTokenResolver();
        Set<String> publicas = Set.of(PUBLICAS_IDENTIDAD);
        return peticion -> publicas.contains(peticion.getRequestURI())
                || ("POST".equals(peticion.getMethod()) && "/api/v1/auth/desbloquear".equals(peticion.getRequestURI()))
                ? null : cabecera.resolve(peticion);
    }
}
