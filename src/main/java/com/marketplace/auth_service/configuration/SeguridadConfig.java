package com.marketplace.auth_service.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.identidad.adapter.in.security.AccesoAuthenticationManager;
import com.marketplace.auth_service.identidad.adapter.in.security.IntegracionAuthenticationManager;
import com.marketplace.auth_service.identidad.adapter.in.security.ReglasIntegracion;
import com.marketplace.auth_service.identidad.adapter.in.security.RespuestasAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.port.out.AuditoriaAccesoPort;
import com.marketplace.auth_service.shared.domain.ScopeServicio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;
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

    /** Salud para la plataforma de despliegue (TEC-INFRA-T005): solo UP/DOWN, sin detalles. */
    static final String SALUD = "/actuator/health";

    /** Token de servicio (SPEC-17 RF-17.4): el módulo se autentica con client_id y client_secret, no con Bearer. */
    static final String TOKEN_SERVICIO = "/api/v1/auth/token";

    /**
     * Rutas de integración (SPEC-17/18) que aceptan token de servicio, con el scope
     * que exige cada una. Fuera de esta lista un token de servicio recibe 403
     * {@code TOKEN_NO_APLICABLE}: un módulo no actúa en nombre de una persona (ESC-17.4).
     */
    static final String INTROSPECCION = "/api/v1/auth/introspeccion";
    static final String ROLES = "/api/v1/roles";
    static final String PERMISOS = "/api/v1/permisos";

    /** Rutas públicas de sesión (EP-02-T004/T005; login las amplía) y registro (FEAT-01-T1). */
    static final String[] PUBLICAS_SESION_POST = {
            "/api/v1/auth/registro",
            "/api/v1/auth/login",
            "/api/v1/auth/otp/solicitar",
            "/api/v1/auth/otp/verificar",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    };

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, FirmaPort firmaPort, ObjectMapper json,
                                        ObjectProvider<AuditoriaAccesoPort> auditoria,
                                        ObjectProvider<Clock> reloj) throws Exception {
        AuthenticationManager accesos = new AccesoAuthenticationManager(firmaPort);
        AuthenticationManager integracion = new IntegracionAuthenticationManager(firmaPort);
        // Los contextos parciales de prueba no cargan auditoría: sin ella, el 403 no se registra.
        RespuestasAcceso respuestas = new RespuestasAcceso(json,
                auditoria.getIfAvailable(() -> denegacion -> { }), reloj.getIfAvailable(Clock::systemUTC));
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.GET, PUBLICAS_IDENTIDAD).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLICAS_SESION_POST).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/desbloquear").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/password/politica").permitAll()
                        .requestMatchers(HttpMethod.GET, SALUD).permitAll()
                        .requestMatchers(HttpMethod.POST, TOKEN_SERVICIO).permitAll()
                        .requestMatchers(HttpMethod.POST, INTROSPECCION)
                            .access(ReglasIntegracion.soloServicio(ScopeServicio.TOKENS_INTROSPECCION))
                        .requestMatchers(HttpMethod.GET, ROLES, PERMISOS)
                            .access(ReglasIntegracion.soloServicio(ScopeServicio.ROLES_LEER))
                        .anyRequest().authenticated())
                .oauth2ResourceServer(recurso -> recurso
                        .bearerTokenResolver(soloFueraDeRutasPublicas())
                        .authenticationManagerResolver(peticion -> esDeIntegracion(peticion) ? integracion : accesos)
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
        Set<String> sesionPost = Set.of(PUBLICAS_SESION_POST);
        return peticion -> publicas.contains(peticion.getRequestURI())
                || ("POST".equals(peticion.getMethod()) && sesionPost.contains(peticion.getRequestURI()))
                || ("POST".equals(peticion.getMethod()) && TOKEN_SERVICIO.equals(peticion.getRequestURI()))
                || ("POST".equals(peticion.getMethod()) && "/api/v1/auth/desbloquear".equals(peticion.getRequestURI()))
                || ("GET".equals(peticion.getMethod())
                    && ("/api/v1/password/politica".equals(peticion.getRequestURI())
                        || SALUD.equals(peticion.getRequestURI())))
                ? null : cabecera.resolve(peticion);
    }

    /** Rutas que validan con el gestor de integración (persona o servicio). */
    static boolean esDeIntegracion(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();
        return ("POST".equals(peticion.getMethod()) && INTROSPECCION.equals(ruta))
                || ("GET".equals(peticion.getMethod()) && (ROLES.equals(ruta) || PERMISOS.equals(ruta)));
    }
}
