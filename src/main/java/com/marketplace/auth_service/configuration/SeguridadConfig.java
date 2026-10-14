package com.marketplace.auth_service.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena de seguridad HTTP del monolito (ENTORNO-Y-REACT §2).
 *
 * <p>Stateless y sin cookies: el Bearer viaja en la cabecera, así que no hay
 * sesión HTTP ni CSRF que proteger. Solo son públicas las rutas H3 que de
 * verdad existen; se listan una a una, nunca con un comodín sobre {@code /auth/**}.
 * Todo lo demás exige autenticación.
 *
 * <p>Cada dueño añade aquí sus rutas públicas H3 cuando las implemente
 * (login, refresh, logout, OTP, desbloqueo, política).
 */
@Configuration(proxyBeanMethods = false)
public class SeguridadConfig {

    /** Rutas públicas de identidad (TEC-IDENTIDAD-T003). */
    static final String[] PUBLICAS_IDENTIDAD = {
            "/api/v1/auth/.well-known/jwks.json",
            "/api/v1/auth/.well-known/openid-configuration"
    };

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.GET, PUBLICAS_IDENTIDAD).permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
