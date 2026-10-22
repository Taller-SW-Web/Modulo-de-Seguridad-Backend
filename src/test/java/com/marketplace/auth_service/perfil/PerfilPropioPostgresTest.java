package com.marketplace.auth_service.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.AuditoriaJpaAdapter;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import com.marketplace.auth_service.configuration.AuditoriaConfig;
import com.marketplace.auth_service.configuration.AutorizacionConfig;
import com.marketplace.auth_service.configuration.PerfilConfig;
import com.marketplace.auth_service.configuration.UsuarioConfiguration;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.in.PerfilPort;
import com.marketplace.auth_service.perfil.domain.model.CuentaPerfilNoEncontradaException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
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

/**
 * EP-08-T003 con proveedores reales: {@code PerfilPort} compuesto por
 * {@link PerfilConfig} sobre cuentas, autorización y perfiles persistidos, en el
 * esquema observado y con la semilla real del catálogo. Base desechable por clase.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"},
        showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({UsuarioConfiguration.class, AutorizacionConfig.class, PerfilConfig.class, AuditoriaConfig.class,
        AuditoriaJpaAdapter.class, PerfilPropioPostgresTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PerfilPropioPostgresTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan({"com.marketplace.auth_service.usuario.adapter.out.persistence.entity",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.perfil.adapter.out.persistence.entity",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.entity"})
    @EnableJpaRepositories({"com.marketplace.auth_service.usuario.adapter.out.persistence.repository",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.perfil.adapter.out.persistence.repository",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.repository"})
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
        String nombre = "perfil_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        try (var admin = DriverManager.getConnection(url, usuario, contrasena); var s = admin.createStatement()) {
            s.execute("CREATE DATABASE " + nombre);
        }
        String propia = url.replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
        try (var c = DriverManager.getConnection(propia, usuario, contrasena); var s = c.createStatement()) {
            s.execute(leer("mfa/base-observada/schema.sql"));
            s.execute(leer("db/migration/V106__auditoria_registro_id_y_outbox_reintento.sql"));
            s.execute(leer("db/autorizacion/R__catalogo_autorizacion.sql"));
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

    @Autowired PerfilPort perfil;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("truncate usuario cascade");
    }

    private UUID cuenta(String correo, String celular, String... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, celular, estado) "
                + "values (?, ?, 'María', 'Quispe Rojas', ?, 'ACTIVO'::estado_cuenta)", id, correo, celular);
        for (String rol : roles) {
            jdbc.update("insert into usuario_rol(usuario_id, rol_codigo) values (?, ?::codigo_rol)", id, rol);
        }
        return id;
    }

    private static ActorUsuario actor(UUID sub) {
        return new ActorUsuario(sub, UUID.randomUUID(), Set.of(), Set.of());
    }

    @Test
    void clienteSinPerfilRegistradoRecibeSusDatosYAtributosVacios() {
        UUID ana = cuenta("ana@clientes.test", "+51987654321", "CLIENTE");

        PerfilProyectado p = perfil.leerPropio(actor(ana));

        assertThat(p.id()).isEqualTo(ana);
        assertThat(p.nombreCompleto()).isEqualTo("María Quispe Rojas");
        assertThat(p.celular()).isEqualTo("+51987654321");
        assertThat(p.estado()).isEqualTo("ACTIVO");
        assertThat(p.roles()).containsExactly("CLIENTE");
        assertThat(p.mfaHabilitado()).isFalse();
        assertThat(p.atributosCliente()).isNotNull();
        assertThat(p.atributosCliente().fechaNacimiento()).isNull();
        assertThat(p.atributosVendedor()).isNull();
        assertThat(p.documento()).isNull();
    }

    @Test
    void vendedorConPerfilPersistido() {
        UUID vendedor = cuenta("v@tienda.test", null, "VENDEDOR");
        jdbc.update("insert into perfil_vendedor(usuario_id, codigo_vendedor, tienda, fecha_ingreso) "
                + "values (?, 'VEND-1042', 'Tienda Centro', date '2025-03-01')", vendedor);

        PerfilProyectado p = perfil.leerPropio(actor(vendedor));

        assertThat(p.atributosVendedor().codigoVendedor()).isEqualTo("VEND-1042");
        assertThat(p.atributosVendedor().tienda()).isEqualTo("Tienda Centro");
        assertThat(p.atributosVendedor().fechaIngreso()).isEqualTo(LocalDate.parse("2025-03-01"));
        assertThat(p.atributosCliente()).isNull();
    }

    @Test
    void unRolDeGestionHaceEfectivoElSegundoFactor() {
        UUID gestor = cuenta("g@g7.test", null, "GESTOR_DESPACHO");

        assertThat(perfil.leerPropio(actor(gestor)).mfaHabilitado()).isTrue();
    }

    @Test
    void cadaTitularVeSoloSuPerfil() {
        UUID ana = cuenta("ana@clientes.test", null, "CLIENTE");
        UUID beto = cuenta("beto@clientes.test", null, "CLIENTE");

        assertThat(perfil.leerPropio(actor(ana)).correo()).isEqualTo("ana@clientes.test");
        assertThat(perfil.leerPropio(actor(beto)).correo()).isEqualTo("beto@clientes.test");
    }

    @Test
    void sinClaveAesUnDocumentoCifradoFallaCerradoYNoSeOculta() {
        UUID ana = cuenta("ana@clientes.test", null, "CLIENTE");
        jdbc.update("insert into perfil_cliente(usuario_id, tipo_documento, documento_cifrado, documento_key_id, "
                + "documento_hmac) values (?, 'DNI'::tipo_documento, 'cifrado-opaco', 'k1', ?)", ana, "a".repeat(64));

        assertThatThrownBy(() -> perfil.leerPropio(actor(ana))).isInstanceOf(DocumentoNoLegibleException.class);
    }

    @Test
    void unaCuentaInexistenteNoProduceUnPerfil() {
        assertThatThrownBy(() -> perfil.leerPropio(actor(UUID.randomUUID())))
                .isInstanceOf(CuentaPerfilNoEncontradaException.class);
    }
}
