package com.marketplace.auth_service.sesion.adapter.out.persistence;

import com.fasterxml.jackson.databind.*;
import com.marketplace.auth_service.auditoria.adapter.in.transaction.AuditoriaTransaccional;
import com.marketplace.auth_service.auditoria.adapter.in.web.ActividadController;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.AuditoriaJpaAdapter;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.BaseAuditoriaPostgres;
import com.marketplace.auth_service.auditoria.adapter.out.transaccion.TareasTrasCommitSpring;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.out.AuditoriaPersistenciaPort;
import com.marketplace.auth_service.auditoria.application.service.AuditoriaService;
import com.marketplace.auth_service.configuration.*;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.sesion.adapter.in.web.*;
import com.marketplace.auth_service.sesion.adapter.out.persistence.entity.*;
import com.marketplace.auth_service.sesion.adapter.out.persistence.repository.*;
import com.marketplace.auth_service.sesion.application.dto.*;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.PersistenciaSesionPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** HTTP y PostgreSQL reales: sesiones, cuentas, roles y firma productivos. Con
 * FEAT-12-T2 se integra también auditoría (registro real y /me/actividad);
 * la protección para distinguir bloqueos sigue sin integrarse. */
@SpringBootTest(classes = SesionesIntegracionPostgresTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false"})
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class SesionesIntegracionPostgresTest {
    static final Instant AHORA = Instant.parse("2026-10-15T16:00:00Z");
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @EntityScan(basePackages = {"com.marketplace.auth_service.sesion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.usuario.adapter.out.persistence.entity",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.entity"})
    @EnableJpaRepositories(basePackages = {"com.marketplace.auth_service.sesion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.usuario.adapter.out.persistence.repository",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.repository"})
    @Import({SesionConfig.class, SesionJpaAdapter.class, UsuarioConfiguration.class, AutorizacionConfig.class,
            IdentidadConfig.class, SeguridadConfig.class, RefrescoController.class, CierreController.class,
            SesionErroresAdvice.class, AuditoriaJpaAdapter.class, ActividadController.class})
    static class Aplicacion {
        @Bean Clock reloj() { return Clock.fixed(AHORA, ZoneOffset.UTC); }
        /** Puerto de auditoría real (FEAT-12-T2): igual que AuditoriaConfig, sin cola de reintentos. */
        @Bean AuditoriaPort auditoriaPort(AuditoriaPersistenciaPort filas,
                                          PlatformTransactionManager transacciones) {
            return new AuditoriaTransaccional(new AuditoriaService(filas,
                    new TareasTrasCommitSpring(transacciones)));
        }
    }
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        PG.start();
        try (var conexion = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            BaseSesionPostgres.cargarEsquema(conexion);
            BaseSesionPostgres.migrar(conexion);
            // FEAT-12-T2: V106 completa auditoría_seguridad (registro_id) sobre la base ya cargada.
            BaseAuditoriaPostgres.migrar(conexion);
            conexion.createStatement().execute("insert into rol(codigo,nombre) values ('CLIENTE','Cliente'), ('VENDEDOR','Vendedor')");
        } catch (Exception error) { throw new IllegalStateException("Fixture local de sesión no disponible", error); }
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("identidad.issuer", () -> "https://sesiones.example.test/api/v1/auth");
        r.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }
    @Autowired SesionesPort sesiones;
    @Autowired CuentasPort cuentas;
    @Autowired FirmaPort firma;
    @Autowired PersistenciaSesionPort persistencia;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired ApplicationContext contexto;
    @Autowired AuditoriaPort auditoria;
    @LocalServerPort int puerto;
    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();
    UUID usuario;
    TransactionTemplate tx() { return new TransactionTemplate(manager); }

    @BeforeEach void preparar() {
        usuario = UUID.randomUUID();
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado) values (?,?,'Cliente','Integración','ACTIVO')",
                usuario, usuario + "@example.test");
        jdbc.update("insert into usuario_rol(usuario_id,rol_codigo) values (?,'CLIENTE')", usuario);
    }
    SesionIniciada abrir() {
        return tx().execute(status -> {
            cuentas.bloquear(usuario);
            return sesiones.abrir(usuario, new IdentidadAutorizada(usuario, "Cliente Integración",
                    usuario + "@example.test", List.of("CLIENTE"), List.of()),
                    new ContextoPeticion("127.0.0.1", "sesiones-test", UUID.randomUUID()));
        });
    }
    HttpResponse<String> post(String operacion, String secreto) throws Exception {
        return enviar(operacion, json.writeValueAsString(Map.of("refreshToken", secreto)));
    }
    HttpResponse<String> enviar(String operacion, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/auth/" + operacion))
                .header("Content-Type", "application/json").header("Authorization", "Bearer acceso-vencido")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    UUID familia(String secreto) {
        return jdbc.queryForObject("select familia_id from token_refresco where hash_token=?", UUID.class,
                com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter.sha256Hex(secreto));
    }
    boolean revocada(UUID familia) {
        return jdbc.queryForObject("select revocada from sesion_familia where id=?", Boolean.class, familia);
    }

    @Test void refreshUsaCuentaRolesFirmaYDecoradorReales() throws Exception {
        assertThat(contexto.getBeansOfType(SesionesPort.class)).hasSize(1);
        assertThat(contexto.getBeansOfType(PersistenciaSesionPort.class)).hasSize(1);
        var inicial = abrir();
        jdbc.update("insert into usuario_rol(usuario_id,rol_codigo) values (?,'VENDEDOR')", usuario);
        var respuesta = post("refresh", inicial.refreshToken());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        var cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.path("refreshToken").asText()).isNotEqualTo(inicial.refreshToken());
        assertThat(cuerpo.path("usuario").path("roles").toString()).contains("CLIENTE", "VENDEDOR");
        assertThat(firma.validar(cuerpo.path("accessToken").asText())).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from acceso_sesion where usuario_id=?", Long.class, usuario)).isEqualTo(2);
    }
    @Test void reusoConfirmaRevocacionAntesDel401YMantieneOtraFamilia() throws Exception {
        var primera = abrir();
        var otra = abrir();
        assertThat(post("refresh", primera.refreshToken()).statusCode()).isEqualTo(200);
        assertThat(post("refresh", primera.refreshToken()).statusCode()).isEqualTo(401);
        assertThat(revocada(familia(primera.refreshToken()))).isTrue();
        assertThat(revocada(familia(otra.refreshToken()))).isFalse();
        assertThat(post("refresh", otra.refreshToken()).statusCode()).isEqualTo(200);
    }
    @Test void logoutVencidoRepetidoYDesconocidoEs204YNoCierraOtraFamilia() throws Exception {
        var primera = abrir();
        var otra = abrir();
        jdbc.update("update token_refresco set creado_en=?,expiracion=? where familia_id=?",
                java.sql.Timestamp.from(AHORA.minus(Duration.ofDays(8))),
                java.sql.Timestamp.from(AHORA.minusSeconds(1)), familia(primera.refreshToken()));
        assertThat(post("logout", primera.refreshToken()).statusCode()).isEqualTo(204);
        assertThat(post("logout", primera.refreshToken()).statusCode()).isEqualTo(204);
        assertThat(post("logout", "desconocido").statusCode()).isEqualTo(204);
        assertThat(revocada(familia(primera.refreshToken()))).isTrue();
        assertThat(post("refresh", otra.refreshToken()).statusCode()).isEqualTo(200);
    }
    @Test void cuentaInactivaNoEmiteYBloqueosQuedanPendientesSinSimularProteccion() throws Exception {
        var inicial = abrir();
        jdbc.update("update usuario set estado='INACTIVO' where id=?", usuario);
        assertThat(post("refresh", inicial.refreshToken()).statusCode()).isEqualTo(401);
        jdbc.update("update usuario set estado='ACTIVO' where id=?", usuario);
        for (String tipo : List.of("AUTOMATICO", "MANUAL")) {
            jdbc.update("delete from bloqueo where usuario_id=?", usuario);
            jdbc.update("insert into bloqueo(usuario_id,tipo,motivo) values (?,?::tipo_bloqueo,?)",
                    usuario, tipo, tipo.equals("MANUAL") ? "Fixture manual" : null);
            assertThat(post("refresh", inicial.refreshToken()).statusCode()).isEqualTo(503);
        }
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where usuario_id=?", Long.class, usuario))
                .isEqualTo(1);
        assertThat(revocada(familia(inicial.refreshToken()))).isFalse();
    }
    @Test void tokenUsadoAunqueYaVencidoRevocaSucesorVigente() throws Exception {
        var inicial = abrir();
        var renovada = post("refresh", inicial.refreshToken());
        String sucesor = json.readTree(renovada.body()).path("refreshToken").asText();
        jdbc.update("update token_refresco set creado_en=?,expiracion=? where hash_token=?",
                java.sql.Timestamp.from(AHORA.minus(Duration.ofDays(8))),
                java.sql.Timestamp.from(AHORA.minusSeconds(1)),
                com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter.sha256Hex(inicial.refreshToken()));
        assertThat(post("refresh", inicial.refreshToken()).statusCode()).isEqualTo(401);
        assertThat(revocada(familia(inicial.refreshToken()))).isTrue();
        assertThat(post("refresh", sucesor).statusCode()).isEqualTo(401);
    }
    @Test void dosRenovacionesConLecturasPreviasConservanUnaSolaRotacion() throws Exception {
        var inicial = abrir();
        String hash = com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter.sha256Hex(inicial.refreshToken());
        UUID familia = familia(inicial.refreshToken());
        UUID jti = jdbc.queryForObject("select jti from token_refresco where hash_token=?", UUID.class, hash);
        try (var ejecutor = Executors.newSingleThreadExecutor()) {
            tx().executeWithoutResult(status -> {
                // Forzar L1 antiguo para ambas filas antes de la rotación competidora.
                em.find(TokenRefrescoJpaEntity.class, jti);
                em.find(SesionFamiliaJpaEntity.class, familia);
                try {
                    var resultado = ejecutor.submit(() -> sesiones.renovar(new SecretoRefresco(inicial.refreshToken())))
                            .get(15, TimeUnit.SECONDS);
                    assertThat(resultado).isInstanceOf(RenovacionResultado.Renovada.class);
                } catch (Exception error) { throw new IllegalStateException(error); }
                var resultado = sesiones.renovar(new SecretoRefresco(inicial.refreshToken()));
                assertThat(resultado).isInstanceOf(RenovacionResultado.Rechazada.class);
                assertThat(((RenovacionResultado.Rechazada) resultado).motivo()).isEqualTo(MotivoRechazo.REUSO_DETECTADO);
            });
        }
        assertThat(revocada(familia)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where familia_id=?", Long.class, familia)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where familia_id=? and not revocado", Long.class, familia))
                .isZero();
    }
    @Test void proveedoresExigenTransaccionYResolucionFuncionaDentroDelCoordinador() {
        var inicial = abrir();
        assertThatThrownBy(() -> sesiones.abrir(usuario, new IdentidadAutorizada(usuario, "Cliente",
                usuario + "@example.test", List.of("CLIENTE"), List.of()),
                new ContextoPeticion("127.0.0.1","test",UUID.randomUUID())))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> sesiones.resolverAcceso(new AccesoVerificado(usuario, UUID.randomUUID())))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        tx().executeWithoutResult(status -> {
            cuentas.bloquear(usuario);
            UUID jti = jdbc.queryForObject("select jti from acceso_sesion where familia_id=?", UUID.class, familia(inicial.refreshToken()));
            assertThat(sesiones.resolverAcceso(new AccesoVerificado(usuario,jti)).usuarioId()).isEqualTo(usuario);
        });
    }
    @Test void jsonMalformadoNoDevuelveNiRegistraSecreto(CapturedOutput salida) throws Exception {
        String secreto = "synthetic-do-not-log-refresh";
        var respuesta = enviar("refresh", "{\"refreshToken\":\"" + secreto + "\"");
        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(json.readTree(respuesta.body()).path("code").asText()).isEqualTo("VALIDACION");
        assertThat(respuesta.body()).doesNotContain(secreto);
        assertThat(salida.getAll()).doesNotContain(secreto);
    }

    /** FEAT-12-T2: el logout registra SESION_CERRADA y /me/actividad lo muestra una sola vez. */
    @Test void logoutRegistraSesionCerradaEnActividadSinDuplicar() throws Exception {
        var sesion = abrir();
        UUID familia = familia(sesion.refreshToken());

        assertThat(post("logout", sesion.refreshToken()).statusCode()).isEqualTo(204);

        // El hecho no crítico se escribe tras el commit y en hilo aparte: se espera su llegada.
        var cuerpo = esperarActividad(sesion.accessToken(), 1);
        var registro = cuerpo.path("registros").get(0);
        assertThat(registro.path("accion").asText()).isEqualTo("SESION_CERRADA");
        assertThat(registro.path("resultado").asText()).isEqualTo("EXITO");
        assertThat(registro.path("objetivoUsuarioId").asText()).isEqualTo(usuario.toString());
        assertThat(registro.path("ip").asText()).isEqualTo("127.0.0.1");
        assertThat(registro.path("detalle").path("familiaId").asText()).isEqualTo(familia.toString());
        assertThat(registro.toString()).doesNotContain("refreshToken", sesion.refreshToken(), "Bearer");

        // Logout repetido y refresco desconocido: 204 sin programar un hecho nuevo.
        assertThat(post("logout", sesion.refreshToken()).statusCode()).isEqualTo(204);
        assertThat(post("logout", "refresco-desconocido").statusCode()).isEqualTo(204);
        assertThat(actividad(sesion.accessToken()).path("total").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from auditoria_seguridad where objetivo_usuario_id=? and accion='SESION_CERRADA'",
                Long.class, usuario)).isEqualTo(1);
        assertThat(auditoria.actividadPropia(usuario, 1, 10).registros()).hasSize(1);
    }

    private JsonNode actividad(String accessToken) throws Exception {
        var respuesta = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/auth/me/actividad"))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    /** Espera el registro asíncrono (hilo {@code auditoria-no-critica}) sin fijar una espera ciega. */
    private JsonNode esperarActividad(String accessToken, long esperado) throws Exception {
        JsonNode cuerpo = null;
        long limite = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < limite) {
            cuerpo = actividad(accessToken);
            if (cuerpo.path("total").asLong() >= esperado) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.path("total").asLong()).isEqualTo(esperado);
        return cuerpo;
    }
}
