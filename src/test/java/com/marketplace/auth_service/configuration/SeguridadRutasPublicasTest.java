package com.marketplace.auth_service.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EP-02-T010: solo operaciones públicas H3 precisas, sin comodín
 * {@code /auth/**}. Si un dueño añade su ruta pública, amplía estas listas y
 * este test (previa revisión con Sergio, dueño de la cadena).
 */
class SeguridadRutasPublicasTest {

    @Test
    void publicasExactasSinComodines() {
        assertThat(SeguridadConfig.PUBLICAS_IDENTIDAD).containsExactlyInAnyOrder(
                "/api/v1/auth/.well-known/jwks.json",
                "/api/v1/auth/.well-known/openid-configuration");
        assertThat(SeguridadConfig.PUBLICAS_SESION_POST).containsExactlyInAnyOrder(
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/login");
        assertThat(SeguridadConfig.PUBLICAS_IDENTIDAD).allMatch(ruta -> !ruta.contains("*"));
        assertThat(SeguridadConfig.PUBLICAS_SESION_POST).allMatch(ruta -> !ruta.contains("*"));
    }
}
