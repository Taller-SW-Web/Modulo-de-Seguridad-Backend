package com.marketplace.auth_service.identidad.adapter.in.web;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.dto.ClienteTecnico;
import com.marketplace.auth_service.identidad.application.dto.ResultadoGestionCliente;
import com.marketplace.auth_service.identidad.application.dto.ScopeAsignado;
import com.marketplace.auth_service.identidad.application.dto.ScopeDisponible;
import com.marketplace.auth_service.identidad.application.dto.SecretoEntregado;
import com.marketplace.auth_service.identidad.application.port.in.ClientesTecnicosPort;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Superficie HTTP de SPEC-19 en /api/v2 (contrato propuesto de FEAT-14). */
@WebMvcTest(ClientesTecnicosController.class)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({SeguridadConfig.class, ClientesTecnicosController.class})
class ClientesTecnicosHttpTest {

    static final String BASE = "/api/v2/clientes-servicio";
    static final ClienteTecnico CLIENTE = new ClienteTecnico("modulo-prueba-h5", "Cliente de prueba H5", true,
            List.of(new ScopeAsignado("usuarios:leer", "api-seguridad")));

    @Autowired MockMvc http;
    @MockitoBean ClientesTecnicosPort clientes;
    @MockitoBean FirmaPort firma;

    @BeforeEach
    void acceso() {
        when(firma.validar("acceso")).thenReturn(new AccesoValidado(UUID.randomUUID(), UUID.randomUUID(),
                List.of("ADMIN_SISTEMA"), List.of("integracion.gestionar"), Instant.now().plusSeconds(900)));
    }

    @Test
    void exigeTokenDePersonaYRechazaElDeServicio() throws Exception {
        http.perform(get(BASE)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALIDO"));
        when(firma.validar("servicio")).thenThrow(new TokenNoAplicableException());
        http.perform(get(BASE).header("Authorization", "Bearer servicio")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOKEN_NO_APLICABLE"));
        verifyNoInteractions(clientes);
    }

    @Test
    void listaSinSecretoNiHash() throws Exception {
        when(clientes.listar(any(), any())).thenReturn(ResultadoGestionCliente.correcto(List.of(CLIENTE)));

        http.perform(get(BASE).header("Authorization", "Bearer acceso")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId").value("modulo-prueba-h5"))
                .andExpect(jsonPath("$[0].activo").value(true))
                .andExpect(jsonPath("$[0].scopes[0].codigo").value("usuarios:leer"))
                .andExpect(jsonPath("$[0].scopes[0].audiencia").value("api-seguridad"))
                .andExpect(jsonPath("$[0].clientSecret").doesNotExist());
    }

    @Test
    void catalogoDeScopes() throws Exception {
        when(clientes.catalogo(any(), any())).thenReturn(ResultadoGestionCliente.correcto(
                List.of(new ScopeDisponible("roles:leer", "api-seguridad", "Catálogo de roles y de permisos"))));

        http.perform(get("/api/v2/scopes").header("Authorization", "Bearer acceso")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("roles:leer"))
                .andExpect(jsonPath("$[0].descripcion").value("Catálogo de roles y de permisos"));
    }

    @Test
    void altaResponde201ConElSecretoYNoStore() throws Exception {
        when(clientes.crear(any(), eq("modulo-prueba-h5"), eq("Cliente de prueba H5"), eq(List.of("usuarios:leer")),
                any())).thenReturn(ResultadoGestionCliente.correcto(new SecretoEntregado(CLIENTE, "s3cr3t-una-vez")));

        http.perform(post(BASE).header("Authorization", "Bearer acceso").contentType("application/json")
                        .content("{\"clientId\":\"modulo-prueba-h5\",\"nombre\":\"Cliente de prueba H5\",\"scopes\":[\"usuarios:leer\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.clientSecret").value("s3cr3t-una-vez"))
                .andExpect(jsonPath("$.scopes[0].codigo").value("usuarios:leer"));
    }

    @Test
    void altaSinScopesEnviaListaVacia() throws Exception {
        when(clientes.crear(any(), any(), any(), any(), any()))
                .thenReturn(ResultadoGestionCliente.correcto(new SecretoEntregado(CLIENTE, "x")));

        http.perform(post(BASE).header("Authorization", "Bearer acceso").contentType("application/json")
                .content("{\"clientId\":\"modulo-prueba-h5\",\"nombre\":\"Cliente\"}")).andExpect(status().isCreated());

        verify(clientes).crear(any(), eq("modulo-prueba-h5"), eq("Cliente"), eq(List.of()), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"clientId\":\"modulo-x\",\"nombre\":\"N\",\"secreto\":\"elegido\"}",
            "{\"clientId\":7,\"nombre\":\"N\"}",
            "{\"clientId\":\"modulo-x\"}",
            "{\"clientId\":\"modulo-x\",\"nombre\":\"N\",\"scopes\":\"usuarios:leer\"}",
            "{\"clientId\":\"modulo-x\",\"nombre\":\"N\",\"scopes\":[1]}",
            "no-es-json"})
    void altaConCuerpoInvalidoEs400SinLlamarAlServicio(String cuerpo) throws Exception {
        http.perform(post(BASE).header("Authorization", "Bearer acceso").contentType("application/json").content(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"));
        verifyNoInteractions(clientes);
    }

    @ParameterizedTest
    @CsvSource({"VALIDACION,400", "SCOPE_INSUFICIENTE,403", "NO_ENCONTRADO,404", "CLIENTE_SERVICIO_EXISTENTE,409"})
    void erroresPublicados(String codigo, int estado) throws Exception {
        when(clientes.crear(any(), any(), any(), any(), any())).thenReturn(ResultadoGestionCliente.rechazo(codigo));

        http.perform(post(BASE).header("Authorization", "Bearer acceso").contentType("application/json")
                        .content("{\"clientId\":\"modulo-x1\",\"nombre\":\"N\"}"))
                .andExpect(status().is(estado)).andExpect(jsonPath("$.code").value(codigo))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void validacionIndicaElCampo() throws Exception {
        when(clientes.conceder(any(), any(), any(), any())).thenReturn(ResultadoGestionCliente.invalido("scope"));

        http.perform(post(BASE + "/modulo-x1/scopes").header("Authorization", "Bearer acceso")
                        .contentType("application/json").content("{\"scope\":\"pagos:cobrar\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("scope"));
    }

    @Test
    void rotarDevuelve200ConElSecretoNuevo() throws Exception {
        when(clientes.rotarSecreto(any(), eq("modulo-prueba-h5"), any()))
                .thenReturn(ResultadoGestionCliente.correcto(new SecretoEntregado(CLIENTE, "nuevo")));

        http.perform(post(BASE + "/modulo-prueba-h5/secreto").header("Authorization", "Bearer acceso"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clientSecret").value("nuevo"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void estadoYScopesResponden204() throws Exception {
        when(clientes.cambiarEstado(any(), any(), anyBoolean(), any())).thenReturn(ResultadoGestionCliente.correcto(null));
        when(clientes.conceder(any(), any(), any(), any())).thenReturn(ResultadoGestionCliente.correcto(null));
        when(clientes.revocar(any(), any(), any(), any())).thenReturn(ResultadoGestionCliente.correcto(null));

        http.perform(post(BASE + "/modulo-x1/desactivar").header("Authorization", "Bearer acceso"))
                .andExpect(status().isNoContent());
        http.perform(post(BASE + "/modulo-x1/reactivar").header("Authorization", "Bearer acceso"))
                .andExpect(status().isNoContent());
        http.perform(post(BASE + "/modulo-x1/scopes").header("Authorization", "Bearer acceso")
                .contentType("application/json").content("{\"scope\":\"roles:leer\"}")).andExpect(status().isNoContent());
        http.perform(delete(BASE + "/modulo-x1/scopes/roles:leer").header("Authorization", "Bearer acceso"))
                .andExpect(status().isNoContent());

        verify(clientes).cambiarEstado(any(), eq("modulo-x1"), eq(false), any());
        verify(clientes).cambiarEstado(any(), eq("modulo-x1"), eq(true), any());
        verify(clientes).revocar(any(), eq("modulo-x1"), eq("roles:leer"), any());
    }

    @Test
    void concederSinCuerpoCerradoEs400() throws Exception {
        http.perform(post(BASE + "/modulo-x1/scopes").header("Authorization", "Bearer acceso")
                        .contentType("application/json").content("{\"scope\":\"roles:leer\",\"extra\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(clientes);
    }

    @Test
    void falloTecnicoEs503SinDetallesInternos() throws Exception {
        when(clientes.rotarSecreto(any(), any(), any())).thenThrow(new IllegalStateException("privado"));

        http.perform(post(BASE + "/modulo-x1/secreto").header("Authorization", "Bearer acceso"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("NO_DISPONIBLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("privado"))));
    }
}
