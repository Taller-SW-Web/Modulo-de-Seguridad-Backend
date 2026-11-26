package com.marketplace.auth_service.usuario.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.usuario.application.dto.ResultadoActivacion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.port.in.ActivacionCuentaPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** FEAT-13-T1: contrato HTTP de {@code POST /api/v1/auth/activar-cuenta} con el caso de uso simulado. */
@WebMvcTest(ActivacionCuentaController.class)
@Import({SeguridadConfig.class, ActivacionCuentaController.class})
class ActivacionCuentaHttpTest {

    /** Propia: el paquete ya tiene otras @SpringBootConfiguration de pruebas HTTP y la búsqueda sería ambigua. */
    @SpringBootConfiguration
    static class Aplicacion { }

    private static final String CORRECTO = "{\"token\":\"tok\",\"contrasena\":\"Activa#2026x\",\"aceptaTerminos\":true}";

    @Autowired MockMvc http;
    @MockitoBean ActivacionCuentaPort activacion;
    @MockitoBean FirmaPort firma;

    @BeforeEach
    void preparar() {
        when(activacion.activar(any(), any(), any())).thenReturn(ResultadoActivacion.Estado.ACTIVADA);
    }

    private static MockHttpServletRequestBuilder activar(String cuerpo) {
        return post("/api/v1/auth/activar-cuenta").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test
    void esPublicaEIgnoraUnBearer() throws Exception {
        http.perform(activar(CORRECTO).header("Authorization", "Bearer cualquiera"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(activacion).activar(eq("tok"), eq("Activa#2026x"), any());
        verifyNoInteractions(firma);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"contrasena\":\"Activa#2026x\",\"aceptaTerminos\":true}",
            "{\"token\":\" \",\"contrasena\":\"Activa#2026x\",\"aceptaTerminos\":true}",
            "{\"token\":\"tok\",\"aceptaTerminos\":true}",
            "{\"token\":\"tok\",\"contrasena\":\"Activa#2026x\"}",
            "{\"token\":\"tok\",\"contrasena\":\"Activa#2026x\",\"aceptaTerminos\":false}"})
    void faltaUnDatoOLaAceptacionEs400(String cuerpo) throws Exception {
        http.perform(activar(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
        verifyNoInteractions(activacion);
    }

    @ParameterizedTest
    @CsvSource({"EXPIRADA,ENLACE_EXPIRADO", "YA_USADA,ENLACE_YA_USADO", "INVALIDA,ENLACE_YA_USADO"})
    void enlaceVencidoUsadoODesconocidoEs410(ResultadoActivacion.Estado estado, String codigo) throws Exception {
        when(activacion.activar(any(), any(), any())).thenReturn(estado);
        http.perform(activar(CORRECTO))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value(codigo));
    }

    @Test
    void contrasenaFueraDeLaPoliticaEs422ConCadaRegla() throws Exception {
        when(activacion.activar(any(), any(), any())).thenReturn(new ResultadoActivacion.PoliticaIncumplida(List.of(
                new ResultadoRegistro.ReglaIncumplida("LONGITUD_MINIMA", 12),
                new ResultadoRegistro.ReglaIncumplida("CARACTER_ESPECIAL", null))));
        http.perform(activar(CORRECTO))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("POLITICA_INCUMPLIDA"))
                .andExpect(jsonPath("$.errores[0].regla").value("LONGITUD_MINIMA"))
                .andExpect(jsonPath("$.errores[0].esperado").value(12))
                .andExpect(jsonPath("$.errores[1].regla").value("CARACTER_ESPECIAL"));
    }
}
