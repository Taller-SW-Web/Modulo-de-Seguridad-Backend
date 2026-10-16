package com.marketplace.auth_service.identidad.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin clave ni issuer configurados, JWKS y descubrimiento responden
 * {@code 503 NO_DISPONIBLE} (contrato), sin revelar el motivo interno.
 */
@SpringBootTest(classes = DescubrimientoHttpTest.Aplicacion.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {"identidad.issuer=", "identidad.clave-privada=", "identidad.clave-privada-archivo="})
class DescubrimientoNoDisponibleTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void elJwksResponde503ConElProblemaDelContrato() throws Exception {
        mvc.perform(get("/api/v1/auth/.well-known/jwks.json"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://g7.unmsm.pe/errores/no-disponible"))
                .andExpect(jsonPath("$.title").value("Servicio no disponible"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("NO_DISPONIBLE"))
                .andExpect(jsonPath("$.detail").value("Usa tu copia en caché del JWKS."));
    }

    @Test
    void elDescubrimientoTambienResponde503() throws Exception {
        mvc.perform(get("/api/v1/auth/.well-known/openid-configuration"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("NO_DISPONIBLE"))
                .andExpect(jsonPath("$.detail").value("Usa tu copia en caché del JWKS."));
    }
}
