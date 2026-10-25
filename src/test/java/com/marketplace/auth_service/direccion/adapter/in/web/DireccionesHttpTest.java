package com.marketplace.auth_service.direccion.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.direccion.DoblesDirecciones;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort;
import com.marketplace.auth_service.direccion.application.service.DireccionesService;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.TokensDePrueba;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code GET} y {@code POST /api/v1/usuarios/{id}/direcciones} con el filtro de acceso real
 * (FEAT-04-T1, parte HTTP). El servicio es el real; los puertos de salida son dobles en
 * memoria, así que aquí se prueban contrato, titularidad, validación y evento. La
 * persistencia con PostgreSQL la prueba {@code DireccionesPersistidasPostgresTest}.
 */
@SpringBootTest(classes = DireccionesHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DireccionesHttpTest {

    private static final String ISSUER = "https://seguridad.ejemplo.test/api/v1/auth";
    private static final List<String> ORDEN = new ArrayList<>();
    private static final DoblesDirecciones.Persistencia PERSISTENCIA = new DoblesDirecciones.Persistencia(ORDEN);
    private static final DoblesDirecciones.Cuentas CUENTAS = new DoblesDirecciones.Cuentas(ORDEN);
    private static final DoblesDirecciones.Eventos EVENTOS = new DoblesDirecciones.Eventos();
    private static final DoblesDirecciones.Autorizacion AUTORIZACION = new DoblesDirecciones.Autorizacion();
    private static final DoblesDirecciones.Auditoria AUDITORIA = new DoblesDirecciones.Auditoria();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, DireccionesController.class,
            DireccionesErroresAdvice.class})
    static class Aplicacion {
        @Bean
        DireccionesPort direccionesPort() {
            return new DireccionesService(PERSISTENCIA, CUENTAS, EVENTOS, AUTORIZACION, AUDITORIA);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("identidad.issuer", () -> ISSUER);
        registro.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
        registro.add("spring.mvc.problemdetails.enabled", () -> "true");
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private FirmaPort firmaPort;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper json = new ObjectMapper();
    private final UUID titular = UUID.randomUUID();

    @BeforeEach
    void reiniciar() {
        ORDEN.clear();
        PERSISTENCIA.filas.clear();
        PERSISTENCIA.duenos.clear();
        PERSISTENCIA.llamadas = 0;
        CUENTAS.existentes.clear();
        CUENTAS.existentes.add(titular);
        CUENTAS.consultas = 0;
        CUENTAS.bloqueos = 0;
        EVENTOS.publicados.clear();
        AUTORIZACION.conUsuarioVer.clear();
        AUTORIZACION.adminsSistema.clear();
        AUDITORIA.registradas.clear();
    }

    private HttpResponse<String> enviar(String metodo, String id, String cuerpo, String bearer)
            throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(
                URI.create("http://localhost:" + puerto + "/api/v1/usuarios/" + id + "/direcciones"));
        if (cuerpo == null) {
            peticion.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            peticion.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(cuerpo));
        }
        if (bearer != null) {
            peticion.header("Authorization", "Bearer " + bearer);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> listar(String id, String bearer) throws IOException, InterruptedException {
        return enviar("GET", id, null, bearer);
    }

    private HttpResponse<String> crear(String id, String cuerpo, String bearer)
            throws IOException, InterruptedException {
        return enviar("POST", id, cuerpo, bearer);
    }

    private String tokenDe(UUID sub, List<String> roles, List<String> permisos) {
        return firmaPort.firmar(new ClaimsAcceso(sub, "otra@ejemplo.test", roles, permisos)).token();
    }

    private String token() {
        return firmaPort.firmar(new ClaimsAcceso(titular, "ana@ejemplo.test", List.of("CLIENTE"), List.of())).token();
    }

    private JsonNode problema(HttpResponse<String> respuesta) throws IOException {
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
        return json.readTree(respuesta.body());
    }

    private static String cuerpo(String etiqueta, boolean predeterminada) {
        return "{\"etiqueta\":\"" + etiqueta + "\",\"departamento\":\"Lima\",\"provincia\":\"Lima\","
                + "\"distrito\":\"Miraflores\",\"direccion\":\"Av. Larco 1234, dpto. 502\","
                + "\"referencia\":\"Frente al parque Kennedy\",\"esPredeterminada\":" + predeterminada + "}";
    }

    @Test
    void sinDireccionesResponde200ConListaVacia() throws Exception {
        HttpResponse<String> respuesta = listar(titular.toString(), token());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("direcciones").isArray()).isTrue();
        assertThat(cuerpo.get("direcciones")).isEmpty();
    }

    @Test
    void crearResponde201ConLaDireccionYSinDatosAjenos() throws Exception {
        HttpResponse<String> respuesta = crear(titular.toString(), cuerpo("Casa", false), token());

        assertThat(respuesta.statusCode()).isEqualTo(201);
        JsonNode d = json.readTree(respuesta.body());
        assertThat(UUID.fromString(d.get("id").asText())).isNotNull();
        assertThat(d.get("etiqueta").asText()).isEqualTo("Casa");
        assertThat(d.get("departamento").asText()).isEqualTo("Lima");
        assertThat(d.get("distrito").asText()).isEqualTo("Miraflores");
        assertThat(d.get("direccion").asText()).isEqualTo("Av. Larco 1234, dpto. 502");
        assertThat(d.get("referencia").asText()).isEqualTo("Frente al parque Kennedy");
        assertThat(d.get("esPredeterminada").asBoolean()).as("la primera es predeterminada").isTrue();
        assertThat(d.has("usuarioId")).isFalse();
    }

    @Test
    void crearPublicaUnEventoDeActualizacionDelTitular() throws Exception {
        crear(titular.toString(), cuerpo("Casa", false), token());

        assertThat(EVENTOS.publicados).containsExactly(titular);
        assertThat(ORDEN).containsExactly("bloquear", "crear");
    }

    @Test
    void laListaDevuelveLoCreadoConLaPredeterminadaPrimeroYUnaSola() throws Exception {
        crear(titular.toString(), cuerpo("Casa", true), token());
        crear(titular.toString(), cuerpo("Trabajo", true), token());

        JsonNode lista = json.readTree(listar(titular.toString(), token()).body()).get("direcciones");

        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).get("etiqueta").asText()).isEqualTo("Trabajo");
        assertThat(lista.get(0).get("esPredeterminada").asBoolean()).isTrue();
        assertThat(lista.get(1).get("esPredeterminada").asBoolean()).as("nunca dos a la vez").isFalse();
    }

    @Test
    void otroUsuarioRecibe403NeutroSinEfectoNiConsulta() throws Exception {
        UUID ajeno = UUID.randomUUID();
        CUENTAS.existentes.add(ajeno);

        HttpResponse<String> lectura = listar(ajeno.toString(), token());
        HttpResponse<String> alta = crear(ajeno.toString(), cuerpo("Casa", true), token());

        assertThat(lectura.statusCode()).isEqualTo(403);
        assertThat(problema(lectura).get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(alta.statusCode()).isEqualTo(403);
        assertThat(problema(alta).get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(PERSISTENCIA.llamadas).isZero();
        assertThat(CUENTAS.consultas).isZero();
        assertThat(EVENTOS.publicados).isEmpty();
    }

    @Test
    void unIdAjenoInexistenteSeRespondeIgualQueUnoExistente() throws Exception {
        UUID existente = UUID.randomUUID();
        CUENTAS.existentes.add(existente);

        HttpResponse<String> conCuenta = listar(existente.toString(), token());
        HttpResponse<String> sinCuenta = listar(UUID.randomUUID().toString(), token());

        assertThat(sinCuenta.statusCode()).isEqualTo(conCuenta.statusCode()).isEqualTo(403);
        assertThat(problema(sinCuenta).get("code").asText()).isEqualTo(problema(conCuenta).get("code").asText());
    }

    @Test
    void laCuentaPropiaQueYaNoExisteResponde404() throws Exception {
        CUENTAS.existentes.clear();

        HttpResponse<String> respuesta = listar(titular.toString(), token());

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("NO_ENCONTRADO");
    }

    @Test
    void faltarUnCampoObligatorioResponde400IndicandoElCampoSinEscribir() throws Exception {
        String sinDistrito = "{\"departamento\":\"Lima\",\"provincia\":\"Lima\",\"direccion\":\"Av. Larco 1\"}";

        HttpResponse<String> respuesta = crear(titular.toString(), sinDistrito, token());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        JsonNode problema = problema(respuesta);
        assertThat(problema.get("code").asText()).isEqualTo("VALIDACION");
        assertThat(problema.get("errores").toString()).contains("distrito");
        assertThat(PERSISTENCIA.llamadas).isZero();
        assertThat(EVENTOS.publicados).isEmpty();
    }

    @Test
    void unCampoObligatorioSoloConEspaciosResponde400() throws Exception {
        String cuerpo = "{\"departamento\":\"   \",\"provincia\":\"Lima\",\"distrito\":\"Miraflores\","
                + "\"direccion\":\"Av. Larco 1\"}";

        HttpResponse<String> respuesta = crear(titular.toString(), cuerpo, token());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(problema(respuesta).get("errores").toString()).contains("departamento");
        assertThat(PERSISTENCIA.llamadas).isZero();
    }

    @Test
    void superarUnMaximoDelContratoResponde400() throws Exception {
        String largo = "x".repeat(51);

        HttpResponse<String> respuesta = crear(titular.toString(), cuerpo(largo, false), token());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(problema(respuesta).get("errores").toString()).contains("etiqueta");
        assertThat(respuesta.body()).as("no repite el valor enviado").doesNotContain(largo);
        assertThat(EVENTOS.publicados).isEmpty();
    }

    @Test
    void unCuerpoQueNoEsJsonResponde400() throws Exception {
        HttpResponse<String> respuesta = crear(titular.toString(), "esto no es json", token());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("VALIDACION");
        assertThat(PERSISTENCIA.llamadas).isZero();
    }

    @Test
    void unIdQueNoEsUuidResponde400() throws Exception {
        HttpResponse<String> respuesta = listar("no-es-uuid", token());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("VALIDACION");
    }

    @Test
    void sinTokenResponde401() throws Exception {
        HttpResponse<String> lectura = listar(titular.toString(), null);
        HttpResponse<String> alta = crear(titular.toString(), cuerpo("Casa", true), null);

        assertThat(lectura.statusCode()).isEqualTo(401);
        assertThat(alta.statusCode()).isEqualTo(401);
        assertThat(problema(alta).get("code").asText()).isEqualTo("TOKEN_INVALIDO");
        assertThat(PERSISTENCIA.llamadas).isZero();
    }

    @Test
    void unTokenDeServicioSinElScopeDeDireccionesResponde403ScopeInsuficiente() throws Exception {
        // SPEC-18 FEAT-06-T3: la ruta acepta token de servicio desde esta tarea, pero exige
        // direcciones:leer; usuarios:leer (el scope por defecto del doble) no alcanza.
        String servicio = TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER).build());

        HttpResponse<String> respuesta = listar(titular.toString(), servicio);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(PERSISTENCIA.llamadas).isZero();
    }

    @Test
    void unTokenDeServicioConDireccionesLeerListaSinAutorizarPorTitularYAudita() throws Exception {
        crear(titular.toString(), cuerpo("Casa", true), token());
        AUDITORIA.registradas.clear();
        String servicio = TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER)
                .claim("scope", "direcciones:leer").subject("modulo-despacho").build());

        HttpResponse<String> respuesta = listar(titular.toString(), servicio);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("direcciones")).hasSize(1);
        assertThat(AUDITORIA.registradasServicio).containsExactly(
                new DoblesDirecciones.Auditoria.LlamadaServicio("modulo-despacho", titular));
        assertThat(AUDITORIA.registradas).as("no se mezcla con la auditoría de alta del titular").isEmpty();
    }

    @Test
    void unTokenDeServicioSobreCuentaInexistenteResponde404() throws Exception {
        CUENTAS.existentes.clear();
        String servicio = TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER)
                .claim("scope", "direcciones:leer").build());

        HttpResponse<String> respuesta = listar(titular.toString(), servicio);

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(AUDITORIA.registradasServicio).isEmpty();
    }

    @Test
    void crearRegistraUnaAuditoriaDelTitularConElContextoDeLaPeticion() throws Exception {
        HttpResponse<String> respuesta = crear(titular.toString(), cuerpo("Casa", true), token());

        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(AUDITORIA.registradas).singleElement().satisfies(l -> {
            assertThat(l.actor()).isEqualTo(titular);
            assertThat(l.objetivo()).isEqualTo(titular);
            assertThat(l.contexto().ip()).isNotBlank();
        });
    }

    @Test
    void unaAltaRechazadaNoDejaAuditoriaDeExito() throws Exception {
        crear(titular.toString(), "{\"departamento\":\"Lima\"}", token());
        crear(UUID.randomUUID().toString(), cuerpo("Casa", true), token());

        assertThat(AUDITORIA.registradas).isEmpty();
    }

    @Test
    void quienTieneUsuarioVerHoyLeeLasDireccionesDeUnClienteConToken() throws Exception {
        UUID soporte = UUID.randomUUID();
        AUTORIZACION.conUsuarioVer.add(soporte);

        HttpResponse<String> respuesta = listar(titular.toString(), tokenDe(soporte, List.of("ADMIN_VENTAS"), List.of()));

        assertThat(respuesta.statusCode()).isEqualTo(200);
    }

    @Test
    void elPermisoSoloEnElTokenNoAutorizaLaLecturaAjena() throws Exception {
        UUID soporte = UUID.randomUUID();

        HttpResponse<String> respuesta = listar(titular.toString(),
                tokenDe(soporte, List.of("ADMIN_SISTEMA"), List.of("usuario.ver")));

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(PERSISTENCIA.llamadas).isZero();
    }

    @Test
    void adminSistemaVigenteAnadeEnCuentaAjenaYUsuarioVerNoAlcanza() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID soporte = UUID.randomUUID();
        AUTORIZACION.adminsSistema.add(admin);
        AUTORIZACION.conUsuarioVer.add(soporte);

        HttpResponse<String> deAdmin = crear(titular.toString(), cuerpo("Casa", true),
                tokenDe(admin, List.of("ADMIN_SISTEMA"), List.of()));
        HttpResponse<String> deSoporte = crear(titular.toString(), cuerpo("Otra", true),
                tokenDe(soporte, List.of("ADMIN_VENTAS"), List.of()));

        assertThat(deAdmin.statusCode()).isEqualTo(201);
        assertThat(deSoporte.statusCode()).isEqualTo(403);
        assertThat(EVENTOS.publicados).containsExactly(titular);
        assertThat(AUDITORIA.registradas).singleElement().satisfies(l -> {
            assertThat(l.actor()).isEqualTo(admin);
            assertThat(l.objetivo()).isEqualTo(titular);
        });
    }
}
