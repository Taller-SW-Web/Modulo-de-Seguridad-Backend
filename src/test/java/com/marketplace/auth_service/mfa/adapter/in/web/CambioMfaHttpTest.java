package com.marketplace.auth_service.mfa.adapter.in.web;

import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;
import com.marketplace.auth_service.mfa.application.dto.ResultadoCambioMfa;
import com.marketplace.auth_service.mfa.application.port.in.CambioMfaPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Frontera HTTP y filtro reales; firma y casos de uso son dobles explícitos. */
@WebMvcTest(CambioMfaController.class)
@Import(SeguridadConfig.class)
class CambioMfaHttpTest {
    @Autowired MockMvc http;
    @MockitoBean FirmaPort firma;
    @MockitoBean CambioMfaPort cambios;
    @BeforeEach void configurar() {
        when(firma.validar("acceso-test")).thenReturn(new AccesoValidado(UUID.randomUUID(),UUID.randomUUID(),
                List.of("CLIENTE"),List.of(),Instant.now().plusSeconds(900)));
    }
    @Test void exigeBearerYRechazaServicio() throws Exception {
        http.perform(post("/api/v2/auth/mfa/cambios").contentType("application/json").content("{\"accion\":\"HABILITAR\"}"))
                .andExpect(status().isUnauthorized());
        when(firma.validar("servicio-test")).thenThrow(new TokenNoAplicableException());
        http.perform(post("/api/v2/auth/mfa/cambios").header("Authorization","Bearer servicio-test")
                .contentType("application/json").content("{\"accion\":\"HABILITAR\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TOKEN_NO_APLICABLE"));
        verifyNoInteractions(cambios);
    }
    @Test void formatos202Y204Exactos() throws Exception {
        when(cambios.solicitar(any(),eq("HABILITAR"),any())).thenReturn(new ResultadoCambioMfa.Enviado("token-test","EMAIL","a***@test",300,600));
        http.perform(post("/api/v2/auth/mfa/cambios").header("Authorization","Bearer acceso-test")
                .contentType("application/json").content("{\"accion\":\"HABILITAR\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.mfaChangeToken").value("token-test"))
                .andExpect(jsonPath("$.expiraEn").value(300)).andExpect(jsonPath("$.cambioExpiraEn").value(600))
                .andExpect(jsonPath("$.canal").value("EMAIL"));
        when(cambios.confirmar(any(),eq("token-test"),eq("012345"),any())).thenReturn(ResultadoCambioMfa.Confirmado.EXITO);
        http.perform(post("/api/v2/auth/mfa/cambios/confirmar").header("Authorization","Bearer acceso-test")
                .contentType("application/json").content("{\"mfaChangeToken\":\"token-test\",\"codigo\":\"012345\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }
    @Test void cuerpoCerradoYJsonInvalidoNoLleganAlCaso() throws Exception {
        for(String cuerpo:List.of("{\"accion\":\"HABILITAR\",\"usuarioId\":\"otro\"}","{", "{\"accion\":true}")) {
            http.perform(post("/api/v2/auth/mfa/cambios").header("Authorization","Bearer acceso-test")
                    .contentType("application/json").content(cuerpo))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
        }
        verifyNoInteractions(cambios);
    }
    @Test void codigosIntentosYRetryAfterSiguenContrato() throws Exception {
        when(cambios.confirmar(any(),any(),any(),any())).thenReturn(new ResultadoCambioMfa.Error("CODIGO_INVALIDO",2,null));
        http.perform(post("/api/v2/auth/mfa/cambios/confirmar").header("Authorization","Bearer acceso-test")
                .contentType("application/json").content("{\"mfaChangeToken\":\"token-test\",\"codigo\":\"000000\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.intentosRestantes").value(2));
        when(cambios.reenviar(any(),any(),any())).thenReturn(new ResultadoCambioMfa.Error("DEMASIADAS_SOLICITUDES",null,37L));
        http.perform(post("/api/v2/auth/mfa/cambios/reenviar").header("Authorization","Bearer acceso-test")
                .contentType("application/json").content("{\"mfaChangeToken\":\"token-test\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","37"));
    }
    @Test void errorTecnicoNoExponeDetalles() throws Exception {
        when(cambios.solicitar(any(),any(),any())).thenThrow(new IllegalStateException("detalle interno no público"));
        http.perform(post("/api/v2/auth/mfa/cambios").header("Authorization","Bearer acceso-test")
                .contentType("application/json").content("{\"accion\":\"HABILITAR\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("NO_DISPONIBLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("detalle interno"))));
    }
}
