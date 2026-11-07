package com.marketplace.auth_service.usuario.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAdministracionCuenta;
import com.marketplace.auth_service.usuario.application.port.in.BajaReactivacionPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** FEAT-02: contrato HTTP de la baja y la reactivación con el caso de uso simulado. */
@WebMvcTest(BajaReactivacionController.class)
@Import({SeguridadConfig.class, BajaReactivacionController.class})
class BajaReactivacionHttpTest {

    /** Propia: el paquete ya tiene otras @SpringBootConfiguration de pruebas HTTP y la búsqueda sería ambigua. */
    @SpringBootConfiguration
    static class Aplicacion { }

    private static final String CUENTA = "/api/v1/usuarios/00000000-0000-4000-8000-00000000f00a";

    @Autowired MockMvc http;
    @MockitoBean BajaReactivacionPort operaciones;
    @MockitoBean FirmaPort firma;

    @BeforeEach
    void preparar() {
        when(firma.validar("acceso")).thenReturn(new AccesoValidado(UUID.randomUUID(), UUID.randomUUID(),
                List.of("ADMIN_SISTEMA"), List.of("usuario.desactivar", "usuario.reactivar"), Instant.now().plusSeconds(900)));
        when(operaciones.darDeBaja(any(), any(), any())).thenReturn(ResultadoAdministracionCuenta.HECHO);
        when(operaciones.reactivar(any(), any(), any())).thenReturn(ResultadoAdministracionCuenta.HECHO);
    }

    @Test
    void exigeTokenDePersonaYRechazaElDeServicio() throws Exception {
        http.perform(delete(CUENTA)).andExpect(status().isUnauthorized());
        http.perform(post(CUENTA + "/reactivar")).andExpect(status().isUnauthorized());
        when(firma.validar("servicio")).thenThrow(new TokenNoAplicableException());
        http.perform(delete(CUENTA).header("Authorization", "Bearer servicio"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TOKEN_NO_APLICABLE"));
        verifyNoInteractions(operaciones);
    }

    @Test
    void bajaYReactivacionResponden204SinCuerpo() throws Exception {
        http.perform(delete(CUENTA).header("Authorization", "Bearer acceso"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        http.perform(post(CUENTA + "/reactivar").header("Authorization", "Bearer acceso"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @ParameterizedTest
    @CsvSource({"VALIDACION,400,VALIDACION", "SIN_PERMISO,403,SCOPE_INSUFICIENTE", "NO_ENCONTRADO,404,NO_ENCONTRADO",
            "ADMINISTRADOR_PROTEGIDO,422,ADMINISTRADOR_PROTEGIDO"})
    void erroresDelContrato(ResultadoAdministracionCuenta resultado, int estado, String codigo) throws Exception {
        when(operaciones.darDeBaja(any(), any(), any())).thenReturn(resultado);
        http.perform(delete(CUENTA).header("Authorization", "Bearer acceso"))
                .andExpect(status().is(estado)).andExpect(jsonPath("$.code").value(codigo));
    }

    @Test
    void falloTecnicoEs503SinDetalles() throws Exception {
        when(operaciones.reactivar(any(), any(), any())).thenThrow(new IllegalStateException("privado"));
        http.perform(post(CUENTA + "/reactivar").header("Authorization", "Bearer acceso"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("NO_DISPONIBLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("privado"))));
    }
}
