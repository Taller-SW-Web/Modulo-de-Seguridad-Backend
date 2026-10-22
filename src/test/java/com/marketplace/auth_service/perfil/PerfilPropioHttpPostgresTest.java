package com.marketplace.auth_service.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * EP-08-T007 (parte HTTP): la aplicación completa sobre PostgreSQL real, con el filtro de acceso y la firma
 * RS256 reales (token emitido por {@code FirmaPort}, clave de PRUEBA). Comprueba por HTTP y mirando la base:
 * A ve solo A, el PATCH a B se deniega sin modificar B, los privilegiados/inválidos no dejan cambios parciales,
 * rol/estado/MFA no se asignan por el cuerpo y el evento queda en el outbox con nombres de campo.
 *
 * <p>No acredita login ni MFA reales (el token se firma en la prueba), ni la publicación en RabbitMQ,
 * ni el documento protegido (falta la clave de Christian), ni la edición administrativa (H4).
 */
@SpringBootTest(classes = AuthServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
                "spring.mvc.problemdetails.enabled=true", "g7.eventos.publicar=false"})
@RequierePostgres
class PerfilPropioHttpPostgresTest {

    private static final String ISSUER = "https://seguridad.ejemplo.test/api/v1/auth";
    private static final String EVENTO = "usuario.atributos_actualizados";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) throws Exception {
        BasePerfilPostgres.registrar(registro, "perfil_http");
        registro.add("identidad.issuer", () -> ISSUER);
        registro.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }

    @LocalServerPort int puerto;
    @Autowired FirmaPort firma;
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper json = new ObjectMapper();
    private UUID ana;
    private UUID beto;

    @BeforeEach
    void datos() {
        jdbc.execute("truncate usuario cascade");
        jdbc.execute("truncate outbox");
        ana = cuenta("ana@clientes.test", "+51987654321");
        beto = cuenta("beto@clientes.test", "+51911111111");
    }

    private UUID cuenta(String correo, String celular) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, celular, celular_verificado, estado) "
                + "values (?, ?, 'María', 'Quispe Rojas', ?, true, 'ACTIVO'::estado_cuenta)", id, correo, celular);
        jdbc.update("insert into usuario_rol(usuario_id, rol_codigo) values (?, 'CLIENTE'::codigo_rol)", id);
        return id;
    }

    private String token(UUID id, String correo) {
        return firma.firmar(new ClaimsAcceso(id, correo, List.of("CLIENTE"), List.of())).token();
    }

    private HttpResponse<String> patch(UUID objetivo, String cuerpo, String bearer) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/usuarios/" + objetivo + "/atributos"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + bearer)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> me(String bearer) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/auth/me"))
                .header("Authorization", "Bearer " + bearer).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> fila(UUID id) {
        return jdbc.queryForMap("select nombres, apellidos, celular, celular_verificado, estado::text as estado from usuario where id = ?", id);
    }

    private long eventos() {
        return jdbc.queryForObject("select count(*) from outbox where topico = ?", Long.class, EVENTO);
    }

    @Test
    void cadaUnoVeSoloSuPropiaCuenta() throws Exception {
        JsonNode deAna = json.readTree(me(token(ana, "ana@clientes.test")).body());
        JsonNode deBeto = json.readTree(me(token(beto, "beto@clientes.test")).body());

        assertThat(deAna.get("id").asText()).isEqualTo(ana.toString());
        assertThat(deAna.get("correo").asText()).isEqualTo("ana@clientes.test");
        assertThat(deBeto.get("id").asText()).isEqualTo(beto.toString());
        assertThat(deBeto.toString()).doesNotContain("ana@clientes.test");
        assertThat(deAna.has("documento")).as("nunca en claro").isFalse();
    }

    @Test
    void elPatchPropioPersisteLoVeUnNuevoGetYDejaElEventoConNombresDeCampo() throws Exception {
        String t = token(ana, "ana@clientes.test");

        HttpResponse<String> respuesta = patch(ana, "{\"nombres\":\"Eva Lucía\",\"celular\":\"+51900000000\"}", t);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        Map<String, Object> fila = fila(ana);
        assertThat(fila.get("nombres")).isEqualTo("Eva Lucía");
        assertThat(fila.get("apellidos")).isEqualTo("Quispe Rojas");
        assertThat(fila.get("celular_verificado")).isEqualTo(false);
        JsonNode get = json.readTree(me(t).body());
        assertThat(get.get("nombres").asText()).isEqualTo("Eva Lucía");
        assertThat(get.get("celularVerificado").asBoolean()).isFalse();
        String payload = jdbc.queryForObject("select payload::text from outbox where topico = ?", String.class, EVENTO);
        assertThat(payload).contains("nombres").contains("celular").doesNotContain("Eva Lucía")
                .doesNotContain("+51900000000");
        assertThat(eventos()).isEqualTo(1);
    }

    @Test
    void elPatchALaCuentaDeOtroSeDeniegaYNoModificaNada() throws Exception {
        HttpResponse<String> respuesta = patch(beto, "{\"nombres\":\"Hackeado\"}", token(ana, "ana@clientes.test"));

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(json.readTree(respuesta.body()).get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(fila(beto).get("nombres")).isEqualTo("María");
        assertThat(fila(ana).get("nombres")).isEqualTo("María");
        assertThat(eventos()).isZero();
    }

    @Test
    void unAtributoPrivilegiadoRechazaTodoElCuerpoSinCambiosParciales() throws Exception {
        HttpResponse<String> respuesta = patch(ana, "{\"nombres\":\"Parcial\",\"numeroDocumento\":\"00000000\"}",
                token(ana, "ana@clientes.test"));

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(fila(ana).get("nombres")).isEqualTo("María");
        assertThat(eventos()).isZero();
    }

    @Test
    void unValorInvalidoRechazaTodoElCuerpoSinCambiosParciales() throws Exception {
        HttpResponse<String> respuesta = patch(ana, "{\"nombres\":\"Parcial\",\"celular\":\"123\"}",
                token(ana, "ana@clientes.test"));

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(json.readTree(respuesta.body()).get("code").asText()).isEqualTo("VALIDACION");
        assertThat(respuesta.body()).doesNotContain("123\"");
        assertThat(fila(ana).get("nombres")).isEqualTo("María");
        assertThat(fila(ana).get("celular")).isEqualTo("+51987654321");
        assertThat(eventos()).isZero();
    }

    @Test
    void rolEstadoYMfaNoSeAsignanPorElCuerpo() throws Exception {
        HttpResponse<String> respuesta = patch(ana, "{\"nombres\":\"Solo Nombre\",\"roles\":[\"ADMIN_SISTEMA\"],"
                + "\"estado\":\"INACTIVO\",\"mfaHabilitado\":true,\"correo\":\"otro@x.test\"}", token(ana, "ana@clientes.test"));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        Map<String, Object> fila = fila(ana);
        assertThat(fila.get("nombres")).isEqualTo("Solo Nombre");
        assertThat(fila.get("estado")).isEqualTo("ACTIVO");
        assertThat(jdbc.queryForList("select rol_codigo::text from usuario_rol where usuario_id = ?", String.class, ana))
                .containsExactly("CLIENTE");
        assertThat(jdbc.queryForObject("select correo from usuario where id = ?", String.class, ana))
                .isEqualTo("ana@clientes.test");
    }

    @Test
    void sinTokenOConTokenInvalidoNoHayEfectos() throws Exception {
        HttpResponse<String> respuesta = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/usuarios/" + ana + "/atributos"))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"nombres\":\"Sin token\"}")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(fila(ana).get("nombres")).isEqualTo("María");
    }
}
