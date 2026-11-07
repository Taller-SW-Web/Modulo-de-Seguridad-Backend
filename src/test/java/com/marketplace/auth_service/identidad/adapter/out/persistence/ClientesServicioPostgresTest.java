package com.marketplace.auth_service.identidad.adapter.out.persistence;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import com.marketplace.auth_service.autorizacion.application.dto.RolCatalogado;
import com.marketplace.auth_service.autorizacion.application.port.in.CatalogoPort;
import com.marketplace.auth_service.configuration.AutorizacionConfig;
import com.marketplace.auth_service.identidad.domain.model.ClienteServicio;
import com.marketplace.auth_service.identidad.domain.model.ScopeConcedido;
import com.marketplace.auth_service.identidad.domain.model.SecretoCliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FEAT-05 sobre el esquema observado de Supabase: lectura de clientes y scopes,
 * catálogo de scopes, script de concesiones y catálogo de roles (SPEC-17/18).
 * Cada clase crea su propia base desechable; nunca toca Supabase.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"},
        showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({AutorizacionConfig.class, ClientesServicioPostgresTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClientesServicioPostgresTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository")
    static class Persistencia { }

    private static PostgreSQLContainer<?> contenedor;

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) throws Exception {
        String url = System.getenv("PRUEBAS_POSTGRES_URL");
        String usuario;
        String contrasena;
        if (url != null) {
            usuario = System.getenv().getOrDefault("PRUEBAS_POSTGRES_USUARIO", "postgres");
            contrasena = System.getenv().getOrDefault("PRUEBAS_POSTGRES_CONTRASENA", "postgres");
        } else if (DockerClientFactory.instance().isDockerAvailable()) {
            contenedor = new PostgreSQLContainer<>("postgres:17.6");
            contenedor.start();
            url = contenedor.getJdbcUrl();
            usuario = contenedor.getUsername();
            contrasena = contenedor.getPassword();
        } else {
            return;
        }
        String nombre = "clientes_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        try (var admin = DriverManager.getConnection(url, usuario, contrasena); var s = admin.createStatement()) {
            s.execute("CREATE DATABASE " + nombre);
        }
        String propia = url.replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
        try (var c = DriverManager.getConnection(propia, usuario, contrasena); var s = c.createStatement()) {
            s.execute(leer("mfa/base-observada/schema.sql"));
            s.execute(leer("db/autorizacion/R__catalogo_autorizacion.sql"));
            s.execute(leer("db/identidad/R__catalogo_scopes.sql"));
            s.execute(leer("db/identidad/R__catalogo_scopes.sql")); // repetible: dos veces no cambia nada
        }
        registro.add("spring.datasource.url", () -> propia);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
    }

    private static String leer(String recurso) throws Exception {
        try (var in = new ClassPathResource(recurso).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String concesiones() throws Exception {
        return new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of("docs/migraciones-propuestas/concesiones-servicio.sql")), StandardCharsets.UTF_8);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogoPort catalogo;

    private ClientesServicioJdbcAdapter clientes;

    @BeforeEach
    void datos() {
        jdbc.execute("truncate cliente_servicio_scope, cliente_servicio");
        clientes = new ClientesServicioJdbcAdapter(jdbc);
    }

    private void cliente(String clientId, boolean activo) {
        jdbc.update("insert into cliente_servicio(client_id, client_secret_hash, nombre, activo) values (?, ?, ?, ?)",
                clientId, SecretoCliente.hash("s-" + clientId), clientId, activo);
    }

    @Test
    void elCatalogoDeScopesCumpleLasRestriccionesYAsignaLaAudienciaDeSuDueno() {
        assertThat(jdbc.queryForObject("select count(*) from scope", Integer.class)).isEqualTo(28);
        assertThat(jdbc.queryForList("select distinct audiencia from scope order by 1", String.class))
                .containsExactly("api-despacho", "api-postventa", "api-productos", "api-seguridad", "api-ventas");
        assertThat(jdbc.queryForObject("select audiencia from scope where codigo = 'inventario:reservar'", String.class))
                .isEqualTo("api-productos");
    }

    @Test
    void lasConcesionesSoloLleganAClientesCreadosYSonIdempotentes() throws Exception {
        cliente("modulo-despacho", true);
        cliente("modulo-marketplace", true);

        jdbc.execute(concesiones());
        jdbc.execute(concesiones());

        assertThat(jdbc.queryForObject("select count(*) from cliente_servicio_scope where client_id = 'modulo-chatbot'",
                Integer.class)).as("cliente aún no creado").isZero();
        ClienteServicio despacho = clientes.buscar("modulo-despacho").orElseThrow();
        assertThat(despacho.codigosScopes()).containsExactly("direcciones:leer", "usuarios:leer", "ventas:pedidos:estado");
        assertThat(despacho.audiencias()).containsExactly("api-seguridad", "api-ventas");
        assertThat(clientes.buscar("modulo-marketplace").orElseThrow().codigosScopes())
                .contains("catalogo:leer")
                .doesNotContain("precios:leer")
                .as("catalogo:leer aprobado por G6 el 26-oct; precios:leer sigue pendiente");
    }

    @Test
    void leeElClienteConSuHashEstadoYScopesConAudiencia() {
        cliente("modulo-retail", false);
        jdbc.update("insert into cliente_servicio_scope(client_id, scope) values ('modulo-retail', 'roles:leer')");

        ClienteServicio retail = clientes.buscar("modulo-retail").orElseThrow();

        assertThat(retail.activo()).isFalse();
        assertThat(SecretoCliente.coincide("s-modulo-retail", retail.secretoHash())).isTrue();
        assertThat(retail.scopes()).containsExactly(new ScopeConcedido("roles:leer", "api-seguridad"));
        assertThat(retail.toString()).doesNotContain(retail.secretoHash());
    }

    @Test
    void unClienteSinConcesionesExisteConListaVaciaYUnoDesconocidoNo() {
        cliente("simulador-pasarela", true);

        assertThat(clientes.buscar("simulador-pasarela").orElseThrow().scopes()).isEmpty();
        assertThat(clientes.buscar("modulo-fantasma")).isEmpty();
        assertThat(clientes.buscar("' or '1'='1")).as("parámetro, no SQL interpolado").isEmpty();
    }

    @Test
    void elCatalogoDeRolesSaleDeLaBaseEnOrdenCanonico() {
        List<RolCatalogado> roles = catalogo.roles();

        assertThat(roles).extracting(RolCatalogado::codigo).containsExactly("CLIENTE", "VENDEDOR", "ADMIN_VENTAS",
                "GESTOR_DESPACHO", "GESTOR_COMERCIAL", "ADMIN_SISTEMA");
        assertThat(roles.get(1).permisos()).containsExactly("cliente.buscar", "cliente.registrar");
        assertThat(roles.get(5).permisos()).hasSize(8).contains("usuario.ver", "rol.asignar");
        assertThat(catalogo.permisos("seguridad")).hasSize(10);
        assertThat(catalogo.permisos("ventas")).isEmpty();
    }
}
