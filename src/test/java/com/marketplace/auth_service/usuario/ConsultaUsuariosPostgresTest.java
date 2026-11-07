package com.marketplace.auth_service.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException;
import com.marketplace.auth_service.configuration.AutorizacionConfig;
import com.marketplace.auth_service.configuration.UsuarioConfiguration;
import com.marketplace.auth_service.proteccion.adapter.out.persistence.ProteccionJpaAdapter;
import com.marketplace.auth_service.proteccion.application.port.in.ConsultaBloqueosPort;
import com.marketplace.auth_service.proteccion.application.port.out.PersistenciaProteccionPort;
import com.marketplace.auth_service.proteccion.application.service.ConsultaBloqueosService;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.usuario.application.dto.ConsultaUsuariosQuery;
import com.marketplace.auth_service.usuario.application.dto.UsuarioVista;
import com.marketplace.auth_service.usuario.application.port.in.ConsultaUsuariosPort;
import com.marketplace.auth_service.usuario.domain.exception.ConsultaInvalidaException;
import com.marketplace.auth_service.usuario.domain.exception.CuentaNoEncontradaException;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.Clock;
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
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.init.ScriptUtils;
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
 * EP-01-T003/T004 con proveedores reales: {@code CuentasPort} de cuentas y
 * {@code AutorizacionPort} sobre el esquema observado de Supabase y la semilla
 * real del catálogo. Cada clase crea su propia base desechable.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"},
        showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({UsuarioConfiguration.class, AutorizacionConfig.class, ConsultaUsuariosPostgresTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConsultaUsuariosPostgresTest {

    /**
     * Solo las entidades que componen esta consulta: cuentas, autorización y la lectura
     * de bloqueos de protección (RF-15.6) con su adaptador JPA real.
     */
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan({"com.marketplace.auth_service.usuario.adapter.out.persistence.entity",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.proteccion.adapter.out.persistence.entity"})
    @EnableJpaRepositories({"com.marketplace.auth_service.usuario.adapter.out.persistence.repository",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.proteccion.adapter.out.persistence.repository"})
    @Import(ProteccionJpaAdapter.class)
    static class Persistencia {
        @Bean
        ConsultaBloqueosPort consultaBloqueosPort(PersistenciaProteccionPort persistencia) {
            return new ConsultaBloqueosService(persistencia, Clock.systemUTC());
        }
    }

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
        String nombre = "consulta_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        try (var admin = DriverManager.getConnection(url, usuario, contrasena); var s = admin.createStatement()) {
            s.execute("CREATE DATABASE " + nombre);
        }
        String propia = url.replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
        try (var c = DriverManager.getConnection(propia, usuario, contrasena); var s = c.createStatement()) {
            s.execute(leer("mfa/base-observada/schema.sql"));
            s.execute(leer("db/autorizacion/R__catalogo_autorizacion.sql"));
            // revision de bloqueo (V104) para leer bloqueos con el adaptador real de protección.
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V104__proteccion_racha_huella_revision.sql"));
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

    @Autowired ConsultaUsuariosPort consulta;
    @Autowired JdbcTemplate jdbc;

    private UUID admin;
    private int orden;

    @BeforeEach
    void datos() {
        jdbc.execute("truncate usuario cascade");
        orden = 0;
        admin = cuenta("admin@g7.test", "ACTIVO", "ADMIN_SISTEMA");
    }

    private UUID cuenta(String correo, String estado, String... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado, creado_en) "
                        + "values (?, ?, 'Nombre', 'Apellido', ?::estado_cuenta, ?)",
                id, correo, estado, OffsetDateTime.parse("2026-01-01T00:00:00Z").plusSeconds(++orden));
        for (String rol : roles) {
            jdbc.update("insert into usuario_rol(usuario_id, rol_codigo) values (?, ?::codigo_rol)", id, rol);
        }
        return id;
    }

    private static ActorUsuario actor(UUID sub) {
        return new ActorUsuario(sub, UUID.randomUUID(), Set.of(), Set.of());
    }

    private static ConsultaUsuariosQuery filtro(String estado, String rol, String correo, String pagina,
                                                String tamano) {
        return new ConsultaUsuariosQuery(estado, rol, correo, pagina, tamano);
    }

    @Test
    void filtraPorRolEstadoEfectivoYCorreoAntesDeContarYPaginar() {
        UUID ana = cuenta("ana@clientes.test", "ACTIVO", "CLIENTE");
        cuenta("beto@clientes.test", "INACTIVO", "CLIENTE");
        UUID caro = cuenta("caro@clientes.test", "ACTIVO", "CLIENTE", "VENDEDOR");
        // Bloqueo automático ya vencido: el estado efectivo vuelve a ser ACTIVO.
        jdbc.update("insert into bloqueo(usuario_id, tipo, bloqueado_hasta) "
                + "values (?, 'AUTOMATICO'::tipo_bloqueo, now() - interval '1 hour')", caro);
        // Bloqueo automático vigente: ACTIVO en la tabla, BLOQUEADO para la API.
        UUID dani = cuenta("dani@clientes.test", "ACTIVO", "CLIENTE");
        jdbc.update("insert into bloqueo(usuario_id, tipo, bloqueado_hasta) "
                + "values (?, 'AUTOMATICO'::tipo_bloqueo, now() + interval '1 hour')", dani);
        cuenta("vendedor@tienda.test", "ACTIVO", "VENDEDOR");

        var activosClientes = consulta.listar(actor(admin), filtro("ACTIVO", "CLIENTE", "CLIENTES", null, null));

        assertThat(activosClientes.total()).isEqualTo(2);
        assertThat(activosClientes.elementos()).extracting(UsuarioVista::id).containsExactly(caro, ana);
        UsuarioVista vistaCaro = activosClientes.elementos().getFirst();
        assertThat(vistaCaro.estado()).isEqualTo("ACTIVO");
        assertThat(vistaCaro.roles()).containsExactly("CLIENTE", "VENDEDOR");
        assertThat(vistaCaro.bloqueo()).as("bloqueo vencido no se informa").isNull();

        var bloqueados = consulta.listar(actor(admin), filtro("BLOQUEADO", null, null, null, null));
        assertThat(bloqueados.elementos()).extracting(UsuarioVista::id).containsExactly(dani);
        assertThat(bloqueados.elementos().getFirst().estado()).isEqualTo("BLOQUEADO");
        // RF-15.6: el bloqueo vigente llega desde protección por su puerto exportado.
        var bloqueo = bloqueados.elementos().getFirst().bloqueo();
        assertThat(bloqueo.tipo()).isEqualTo("AUTOMATICO");
        assertThat(bloqueo.hasta()).isNotNull();
        assertThat(bloqueo.motivo()).isNull();
        assertThat(consulta.obtener(actor(admin), dani.toString()).bloqueo()).isEqualTo(bloqueo);
    }

    @Test
    void bloqueoManualSinVencimientoConMotivo() {
        UUID eli = cuenta("eli@clientes.test", "ACTIVO", "CLIENTE");
        jdbc.update("insert into bloqueo(usuario_id, tipo, motivo) "
                + "values (?, 'MANUAL'::tipo_bloqueo, 'Actividad sospechosa')", eli);

        UsuarioVista vista = consulta.obtener(actor(admin), eli.toString());

        assertThat(vista.estado()).isEqualTo("BLOQUEADO");
        assertThat(vista.bloqueo().tipo()).isEqualTo("MANUAL");
        assertThat(vista.bloqueo().hasta()).isNull();
        assertThat(vista.bloqueo().motivo()).isEqualTo("Actividad sospechosa");
    }

    @Test
    void dosPaginasConTotalesReales() {
        for (int i = 0; i < 5; i++) {
            cuenta("c" + i + "@clientes.test", "ACTIVO", "CLIENTE");
        }

        var primera = consulta.listar(actor(admin), filtro(null, "CLIENTE", null, "1", "3"));
        var segunda = consulta.listar(actor(admin), filtro(null, "CLIENTE", null, "2", "3"));

        assertThat(primera.total()).isEqualTo(5);
        assertThat(segunda.total()).isEqualTo(5);
        assertThat(primera.elementos()).hasSize(3);
        assertThat(segunda.elementos()).hasSize(2);
        assertThat(primera.elementos()).extracting(UsuarioVista::id)
                .doesNotContainAnyElementsOf(segunda.elementos().stream().map(UsuarioVista::id).toList());
    }

    @Test
    void unRolSinPersonasDaUnaPaginaVacia() {
        var pagina = consulta.listar(actor(admin), filtro(null, "GESTOR_DESPACHO", null, null, null));

        assertThat(pagina.total()).isZero();
        assertThat(pagina.elementos()).isEmpty();
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamano()).isEqualTo(20);
    }

    @Test
    void elDetalleTraeRolesYMfaEfectivoPorRolDeGestion() {
        UUID gestor = cuenta("gestor@g7.test", "ACTIVO", "GESTOR_COMERCIAL");

        UsuarioVista detalle = consulta.obtener(actor(admin), gestor.toString());

        assertThat(detalle.correo()).isEqualTo("gestor@g7.test");
        assertThat(detalle.roles()).containsExactly("GESTOR_COMERCIAL");
        assertThat(detalle.mfaHabilitado()).isTrue();
    }

    @Test
    void unaCuentaInexistenteDa404SoloDespuesDeAutorizar() {
        UUID cliente = cuenta("cliente@clientes.test", "ACTIVO", "CLIENTE");

        assertThatThrownBy(() -> consulta.obtener(actor(admin), UUID.randomUUID().toString()))
                .isInstanceOf(CuentaNoEncontradaException.class);
        // Sin usuario.ver no se distingue si existe: 403 para un UUID existente y para uno inexistente.
        assertThatThrownBy(() -> consulta.obtener(actor(cliente), admin.toString()))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> consulta.obtener(actor(cliente), UUID.randomUUID().toString()))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void elPermisoSeRevalidaConLaBaseYNoConElToken() {
        UUID exAdmin = cuenta("ex@g7.test", "ACTIVO", "ADMIN_SISTEMA");
        jdbc.update("delete from usuario_rol where usuario_id = ?", exAdmin);
        var conClaimsViejos = new ActorUsuario(exAdmin, UUID.randomUUID(), Set.of("ADMIN_SISTEMA"),
                Set.of("usuario.ver"));

        assertThatThrownBy(() -> consulta.listar(conClaimsViejos, ConsultaUsuariosQuery.sinFiltros()))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void unClienteRecibe403AunqueLosParametrosSeanInvalidos() {
        UUID cliente = cuenta("cliente@clientes.test", "ACTIVO", "CLIENTE");

        assertThatThrownBy(() -> consulta.listar(actor(cliente), filtro("NO_EXISTE", null, null, "0", null)))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> consulta.listar(actor(admin), filtro("NO_EXISTE", null, null, null, null)))
                .isInstanceOf(ConsultaInvalidaException.class);
    }
}
