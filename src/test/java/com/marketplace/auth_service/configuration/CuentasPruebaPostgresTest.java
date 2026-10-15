package com.marketplace.auth_service.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.in.RolesPruebaInterna;
import com.marketplace.auth_service.credencial.adapter.out.persistence.CredencialesJpaAdapter;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CredencialPruebaInterna;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.usuario.application.port.in.AltaCuentaPruebaInterna;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * EP-01-T007 con los proveedores reales de cuentas, credenciales (hasher y política)
 * y autorización, sobre el esquema observado más V101/V105 y el catálogo real.
 * Cada clase crea su propia base desechable; nunca Supabase.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC", "g7.fixtures.cuentas.habilitadas=true"},
        showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({UsuarioConfiguration.class, AutorizacionConfig.class, CredencialesConfig.class, CredencialesJpaAdapter.class,
        CuentasPruebaConfig.class, CuentasPruebaPostgresTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CuentasPruebaPostgresTest {

    private static final String CLAVE = "Zorzal-Andino-2026!";
    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000f00a");
    private static final UUID ADM = UUID.fromString("00000000-0000-4000-8000-00000000f0ad");
    private static final UUID M = UUID.fromString("00000000-0000-4000-8000-00000000f00d");

    /** Solo las entidades de los tres dueños que compone la carga. */
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan({"com.marketplace.auth_service.usuario.adapter.out.persistence.entity",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.credencial.adapter.out.persistence.entity"})
    @EnableJpaRepositories({"com.marketplace.auth_service.usuario.adapter.out.persistence.repository",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.credencial.adapter.out.persistence.repository"})
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
        String nombre = "fixtures_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        try (var admin = DriverManager.getConnection(url, usuario, contrasena); var s = admin.createStatement()) {
            s.execute("CREATE DATABASE " + nombre);
        }
        String propia = url.replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
        try (var c = DriverManager.getConnection(propia, usuario, contrasena); var s = c.createStatement()) {
            s.execute(leer("mfa/base-observada/schema.sql"));
            ScriptUtils.executeSqlScript(c, new FileSystemResource("docs/migraciones-propuestas/V101__credencial_version.sql"));
            ScriptUtils.executeSqlScript(c,
                    new FileSystemResource("docs/migraciones-propuestas/V105__token_un_uso_contexto_id.sql"));
            s.execute(leer("db/autorizacion/R__catalogo_autorizacion.sql"));
        }
        Path comunes = Files.createTempFile("ep01-t007-comunes-", ".txt");
        Files.writeString(comunes, "ComunDePrueba8!\n");
        registro.add("spring.datasource.url", () -> propia);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
        registro.add("g7.fixtures.cuentas.clave", () -> CLAVE);
        registro.add("g7.password.comunes.archivo", comunes::toString);
    }

    private static String leer(String recurso) throws Exception {
        try (var in = new ClassPathResource(recurso).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Autowired CargaCuentasPrueba carga;
    @Autowired CredencialesPort credenciales;
    @Autowired AutorizacionPort autorizacion;
    @Autowired AltaCuentaPruebaInterna altas;
    @Autowired CredencialPruebaInterna credencialesPrueba;
    @Autowired RolesPruebaInterna roles;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired JdbcTemplate jdbc;

    /** La carga ya corrió al arrancar; cada prueba parte de una base vacía y carga de nuevo. */
    @BeforeEach
    void vaciar() {
        jdbc.execute("truncate usuario cascade");
    }

    @Test
    void creaLasSieteCuentasConCredencialYRolesVerificadosPorSusProveedores() {
        var resultados = carga.cargar();

        assertThat(resultados).hasSize(7).allMatch(CargaCuentasPrueba.Resultado::creada);
        assertThat(resultados).extracting(CargaCuentasPrueba.Resultado::etiqueta)
                .containsExactly("A", "B", "V", "ADM", "M", "L", "C");
        assertThat(credenciales.preverificar(Optional.of(A), new SecretoEfimero(CLAVE)).coincide()).isTrue();
        assertThat(credenciales.preverificar(Optional.of(A), new SecretoEfimero(CLAVE + "x")).coincide()).isFalse();
        assertThat(autorizacion.resolver(A).codigosRoles()).containsExactly("CLIENTE");
        assertThat(autorizacion.resolver(ADM).codigosRoles()).containsExactly("ADMIN_SISTEMA");
        assertThat(jdbc.queryForObject("select count(*) from usuario where estado = 'ACTIVO' and correo_verificado "
                + "and not acepta_terminos", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select mfa_habilitado from usuario where id = ?", Boolean.class, M)).isTrue();
        assertThat(jdbc.queryForObject("select mfa_habilitado from usuario where id = ?", Boolean.class, A)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from password_historial", Integer.class)).isEqualTo(7);
    }

    @Test
    void repetirLaCargaNoDuplicaNiSobrescribeCambiosDeLaDemo() {
        carga.cargar();
        String hash = jdbc.queryForObject("select hash_contrasena from credencial where usuario_id = ?", String.class, A);
        jdbc.update("update usuario set nombres = 'Cambiado en demo' where id = ?", A);
        jdbc.update("insert into usuario_rol(usuario_id, rol_codigo) values (?, 'VENDEDOR'::codigo_rol)", A);

        var segunda = carga.cargar();

        assertThat(segunda).hasSize(7).noneMatch(CargaCuentasPrueba.Resultado::creada);
        assertThat(jdbc.queryForObject("select count(*) from usuario", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select nombres from usuario where id = ?", String.class, A))
                .isEqualTo("Cambiado en demo");
        assertThat(jdbc.queryForObject("select hash_contrasena from credencial where usuario_id = ?", String.class, A))
                .isEqualTo(hash);
        assertThat(autorizacion.resolver(A).codigosRoles()).containsExactlyInAnyOrder("CLIENTE", "VENDEDOR");
    }

    @Test
    void claveQueIncumpleLaPoliticaNoCreaNingunaCuenta() {
        var debil = new CargaCuentasPrueba(altas, credencialesPrueba, roles, credenciales, transacciones,
                new SecretoEfimero("corta"));

        assertThatThrownBy(debil::cargar).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LONGITUD_MINIMA").hasMessageNotContaining("corta");
        assertThat(jdbc.queryForObject("select count(*) from usuario", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from credencial", Integer.class)).isZero();
    }

    @Test
    void correoOcupadoPorOtraCuentaRevierteLaCargaCompleta() {
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?, ?, 'Otra', 'Cuenta', "
                + "'ACTIVO')", UUID.randomUUID(), "cliente.b@fixtures.g7.test");

        assertThatThrownBy(carga::cargar).isInstanceOf(IllegalStateException.class);

        List<UUID> creadas = jdbc.queryForList("select id from usuario where correo like '%@fixtures.g7.test' "
                + "and nombres <> 'Otra'", UUID.class);
        assertThat(creadas).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from credencial", Integer.class)).isZero();
    }
}
