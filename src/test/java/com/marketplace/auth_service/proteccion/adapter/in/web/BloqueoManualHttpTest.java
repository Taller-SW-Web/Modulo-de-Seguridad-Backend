package com.marketplace.auth_service.proteccion.adapter.in.web;

import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.proteccion.application.port.in.BloqueoManualPort;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BloqueoManualController.class) @Import({SeguridadConfig.class,BloqueoManualController.class})
class BloqueoManualHttpTest {
    @Autowired MockMvc http;
    @MockitoBean BloqueoManualPort manual;
    @MockitoBean FirmaPort firma;
    final String ruta="/api/v1/usuarios/00000000-0000-4000-8000-00000000f00f/bloquear";
    @BeforeEach void preparar(){when(firma.validar("acceso")).thenReturn(new AccesoValidado(UUID.randomUUID(),UUID.randomUUID(),List.of("ADMIN_SISTEMA"),List.of("cuenta.bloquear"),Instant.now().plusSeconds(900)));}
    @Test void exigePersonaYNoAceptaServicio() throws Exception {
        http.perform(post(ruta).contentType("application/json").content("{\"motivo\":\"Prueba\"}")).andExpect(status().isUnauthorized());
        when(firma.validar("servicio")).thenThrow(new TokenNoAplicableException());
        http.perform(post(ruta).header("Authorization","Bearer servicio").contentType("application/json").content("{\"motivo\":\"Prueba\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TOKEN_NO_APLICABLE"));
        verifyNoInteractions(manual);
    }
    @Test void ambasOperacionesDevuelven204SinTokens() throws Exception {
        http.perform(post(ruta).header("Authorization","Bearer acceso").contentType("application/json").content("{\"motivo\":\"Prueba\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        http.perform(post(ruta.replace("/bloquear","/desbloquear")).header("Authorization","Bearer acceso"))
                .andExpect(status().isNoContent());
    }
    @ParameterizedTest @CsvSource({"VALIDACION,400","SCOPE_INSUFICIENTE,403","CUENTA_NO_DISPONIBLE,403","NO_ENCONTRADO,404","ADMINISTRADOR_PROTEGIDO,422"})
    void erroresPublicados(String code,int status) throws Exception {
        when(manual.ejecutar(any(),any(),anyBoolean(),any(),any())).thenReturn(code);
        http.perform(post(ruta).header("Authorization","Bearer acceso").contentType("application/json").content("{\"motivo\":\"Prueba\"}"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }
    @Test void falloTecnicoEs503SinDetalles() throws Exception {
        when(manual.ejecutar(any(),any(),anyBoolean(),any(),any())).thenThrow(new IllegalStateException("privado"));
        http.perform(post(ruta).header("Authorization","Bearer acceso").contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("NO_DISPONIBLE"));
    }
}
