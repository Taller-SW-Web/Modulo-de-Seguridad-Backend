package com.marketplace.auth_service.usuario.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAltaPersonal;
import com.marketplace.auth_service.usuario.application.dto.SolicitudAltaPersonal;
import com.marketplace.auth_service.usuario.application.dto.UsuarioVista;
import com.marketplace.auth_service.usuario.application.port.in.AltaPersonalPort;
import com.marketplace.auth_service.usuario.application.port.in.ConsultaUsuariosPort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** FEAT-13-T1: contrato HTTP de {@code POST /api/v2/usuarios} con el caso de uso simulado. */
@WebMvcTest(AltaPersonalController.class)
@Import({SeguridadConfig.class, AltaPersonalController.class})
class AltaPersonalHttpTest {

    /** Propia: el paquete ya tiene otras @SpringBootConfiguration de pruebas HTTP y la búsqueda sería ambigua. */
    @SpringBootConfiguration
    static class Aplicacion { }

    private static final String RUTA = "/api/v2/usuarios";
    private static final UUID NUEVA = UUID.fromString("00000000-0000-4000-8000-00000000a13a");

    @Autowired MockMvc http;
    @MockitoBean AltaPersonalPort alta;
    @MockitoBean ConsultaUsuariosPort consulta;
    @MockitoBean FirmaPort firma;

    @BeforeEach
    void preparar() {
        when(firma.validar("acceso")).thenReturn(new AccesoValidado(UUID.randomUUID(), UUID.randomUUID(),
                List.of("ADMIN_SISTEMA"), List.of("usuario.crear", "usuario.ver"), Instant.now().plusSeconds(900)));
        when(alta.autorizar(any(), any())).thenReturn(true);
        when(alta.crear(any(), any(), any())).thenReturn(new ResultadoAltaPersonal.Creada(NUEVA));
        when(consulta.obtener(any(), eq(NUEVA.toString()))).thenReturn(new UsuarioVista(NUEVA, "Gina", "Gestora",
                "Gina Gestora", "gina@ejemplo.com", null, "PENDIENTE_VERIFICACION", false, false, true,
                List.of("GESTOR_COMERCIAL"), null));
    }

    private static MockHttpServletRequestBuilder alta(String cuerpo) {
        return post(RUTA).header("Authorization", "Bearer acceso").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test
    void exigeTokenDePersonaYRechazaElDeServicio() throws Exception {
        http.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        when(firma.validar("servicio")).thenThrow(new TokenNoAplicableException());
        http.perform(post(RUTA).header("Authorization", "Bearer servicio").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TOKEN_NO_APLICABLE"));
        verifyNoInteractions(alta);
    }

    @Test
    void altaCorrectaResponde201ConLaCuentaPendienteYSuRol() throws Exception {
        http.perform(alta("{\"correo\":\" Gina@Ejemplo.com \",\"nombres\":\"Gina\",\"apellidos\":\"Gestora\","
                        + "\"rol\":\"GESTOR_COMERCIAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/usuarios/" + NUEVA))
                .andExpect(jsonPath("$.id").value(NUEVA.toString()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_VERIFICACION"))
                .andExpect(jsonPath("$.roles[0]").value("GESTOR_COMERCIAL"));
        verify(alta).crear(any(), eq(new SolicitudAltaPersonal("gina@ejemplo.com", "Gina", "Gestora",
                Rol.GESTOR_COMERCIAL)), any());
    }

    @Test
    void sinRolCreaLaCuentaSinRol() throws Exception {
        http.perform(alta("{\"correo\":\"repartidor@ejemplo.com\",\"nombres\":\"Rafa\",\"apellidos\":\"Ruta\"}"))
                .andExpect(status().isCreated());
        verify(alta).crear(any(), eq(new SolicitudAltaPersonal("repartidor@ejemplo.com", "Rafa", "Ruta", null)), any());
    }

    @Test
    void sinPermisoResponde403AntesDeValidarElCuerpo() throws Exception {
        when(alta.autorizar(any(), any())).thenReturn(false);
        http.perform(alta("{\"rol\":\"CLIENTE\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SCOPE_INSUFICIENTE"));
        verify(alta, never()).crear(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"correo\":\"gina@ejemplo.com\",\"nombres\":\"Gina\",\"apellidos\":\"Gestora\",\"rol\":\"CLIENTE\"}",
            "{\"correo\":\"gina@ejemplo.com\",\"nombres\":\"Gina\",\"apellidos\":\"Gestora\",\"rol\":\"SUPER_ADMIN\"}",
            "{\"correo\":\"gina@ejemplo.com\",\"nombres\":\"Gina\",\"apellidos\":\"Gestora\",\"contrasena\":\"Secreta#2026\"}",
            "{\"correo\":\"gina@ejemplo.com\",\"nombres\":\"Gina\",\"apellidos\":\"Gestora\",\"roles\":[\"VENDEDOR\"]}",
            "{\"correo\":\"no-es-correo\",\"nombres\":\"Gina\",\"apellidos\":\"Gestora\"}",
            "{\"correo\":\"gina@ejemplo.com\",\"nombres\":\" \",\"apellidos\":\"Gestora\"}",
            "{\"correo\":\"gina@ejemplo.com\",\"nombres\":\"Gina\"}",
            "{}"})
    void cuerpoFueraDelEsquemaEstrictoEs400(String cuerpo) throws Exception {
        http.perform(alta(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"))
                .andExpect(jsonPath("$.errores").isNotEmpty());
        verify(alta, never()).crear(any(), any(), any());
    }

    @Test
    void correoYaRegistradoEs409() throws Exception {
        when(alta.crear(any(), any(), any())).thenReturn(ResultadoAltaPersonal.Rechazo.CORREO_NO_DISPONIBLE);
        http.perform(alta("{\"correo\":\"gina@ejemplo.com\",\"nombres\":\"Gina\",\"apellidos\":\"Gestora\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CORREO_NO_DISPONIBLE"));
    }

    @Test
    void permisoRetiradoDuranteElAltaEs403() throws Exception {
        when(alta.crear(any(), any(), any())).thenReturn(ResultadoAltaPersonal.Rechazo.SIN_PERMISO);
        http.perform(alta("{\"correo\":\"gina@ejemplo.com\",\"nombres\":\"Gina\",\"apellidos\":\"Gestora\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SCOPE_INSUFICIENTE"));
    }

    @Test
    void sinColaDeCorreoEs503() throws Exception {
        when(alta.crear(any(), any(), any())).thenThrow(new VerificacionNoDisponibleException(new IllegalStateException()));
        http.perform(alta("{\"correo\":\"gina@ejemplo.com\",\"nombres\":\"Gina\",\"apellidos\":\"Gestora\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("NO_DISPONIBLE"));
    }
}
