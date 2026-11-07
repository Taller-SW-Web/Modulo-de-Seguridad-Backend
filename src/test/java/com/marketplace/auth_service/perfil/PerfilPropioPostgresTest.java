package com.marketplace.auth_service.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.dto.PaginaActividad;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import com.marketplace.auth_service.configuration.AutorizacionConfig;
import com.marketplace.auth_service.configuration.PerfilConfig;
import com.marketplace.auth_service.configuration.UsuarioConfiguration;
import com.marketplace.auth_service.perfil.application.dto.CambioAdministrativo;
import com.marketplace.auth_service.perfil.application.dto.CampoEditable;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.in.PerfilPort;
import com.marketplace.auth_service.perfil.domain.model.AtributoNoAplicableException;
import com.marketplace.auth_service.perfil.domain.model.CuentaPerfilNoEncontradaException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.perfil.domain.model.EdicionAdministrativaNoPermitidaException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
 * FEAT-04-T6 añade la edición administrativa con escritura real en perfil_vendedor /
 * perfil_cliente; outbox y auditoría son dobles que registran lo recibido.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"},
        showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({UsuarioConfiguration.class, AutorizacionConfig.class, PerfilConfig.class,
        PerfilPropioPostgresTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PerfilPropioPostgresTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan({"com.marketplace.auth_service.usuario.adapter.out.persistence.entity",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.perfil.adapter.out.persistence.entity"})
    @EnableJpaRepositories({"com.marketplace.auth_service.usuario.adapter.out.persistence.repository",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.perfil.adapter.out.persistence.repository"})
    static class Persistencia {
        @Bean
        MensajeriaPort mensajeria() {
            return new MensajeriaPort() {
                @Override
                public UUID guardarEvento(EventoUsuario evento) {
                    EVENTOS.add(evento);
                    return evento.eventoId();
                }

                @Override
                public UUID guardarCorreo(EntregaCorreo correo) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Bean
        AuditoriaPort auditoria() {
            return new AuditoriaPort() {
                @Override
                public void registrarCritica(RegistroSeguro registro) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void registrarNoCriticaTrasCommit(RegistroSeguro registro) {
                    AUDITORIAS.add(registro);
                }

                @Override
                public PaginaActividad actividadPropia(UUID titular, int pagina, int tamano) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    static final List<EventoUsuario> EVENTOS = new CopyOnWriteArrayList<>();
    static final List<RegistroSeguro> AUDITORIAS = new CopyOnWriteArrayList<>();

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
        EVENTOS.clear();
        AUDITORIAS.clear();
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

    // ---------------------------------------------------------------- FEAT-04-T6

    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("10.0.0.1", "prueba", UUID.randomUUID());

    private static <T> CampoEditable<T> om() {
        return CampoEditable.omitido();
    }

    private static <T> CampoEditable<T> pr(T valor) {
        return CampoEditable.presente(valor);
    }

    @Test
    void unAdminCreaLosAtributosDeVendedorYDejaEventoYAuditoria() {
        UUID admin = cuenta("admin@g7.test", null, "ADMIN_SISTEMA");
        UUID vendedor = cuenta("v@tienda.test", null, "VENDEDOR");

        PerfilProyectado p = perfil.actualizarComoAdministrador(actor(admin), vendedor,
                new CambioAdministrativo(om(), om(), om(), om(), pr("VEND-1042"), pr("TIENDA-MIRAFLORES"),
                        pr(LocalDate.parse("2026-03-01")), false), CONTEXTO);

        assertThat(p.atributosVendedor().codigoVendedor()).isEqualTo("VEND-1042");
        assertThat(jdbc.queryForObject("select tienda from perfil_vendedor where usuario_id = ?", String.class,
                vendedor)).isEqualTo("TIENDA-MIRAFLORES");
        assertThat(EVENTOS).singleElement().satisfies(e -> {
            assertThat(e.topico()).isEqualTo("usuario.atributos_actualizados");
            assertThat(e.carga()).containsEntry("campos", "codigoVendedor,tienda,fechaIngreso")
                    .containsEntry("usuarioId", vendedor.toString());
            assertThat(e.carga().values()).doesNotContain("VEND-1042", "TIENDA-MIRAFLORES");
        });
        assertThat(AUDITORIAS).singleElement().satisfies(r -> {
            assertThat(r.accion()).isEqualTo("ATRIBUTOS_ACTUALIZADOS");
            assertThat(r.actorId()).isEqualTo(admin.toString());
            assertThat(r.objetivoUsuarioId()).isEqualTo(vendedor);
            assertThat(r.detalle()).containsEntry("campos", "codigoVendedor,tienda,fechaIngreso");
        });
    }

    @Test
    void unAdminActualizaSoloLaTiendaYConservaElRestoEnLaBase() {
        UUID admin = cuenta("admin@g7.test", null, "ADMIN_SISTEMA");
        UUID vendedor = cuenta("v@tienda.test", null, "VENDEDOR");
        jdbc.update("insert into perfil_vendedor(usuario_id, codigo_vendedor, tienda, fecha_ingreso) "
                + "values (?, 'VEND-1', 'Centro', date '2025-01-02')", vendedor);

        perfil.actualizarComoAdministrador(actor(admin), vendedor,
                new CambioAdministrativo(om(), om(), om(), om(), om(), pr("Miraflores"), om(), false), CONTEXTO);

        var fila = jdbc.queryForMap("select codigo_vendedor, tienda, fecha_ingreso from perfil_vendedor "
                + "where usuario_id = ?", vendedor);
        assertThat(fila.get("codigo_vendedor")).isEqualTo("VEND-1");
        assertThat(fila.get("tienda")).isEqualTo("Miraflores");
        assertThat(fila.get("fecha_ingreso").toString()).isEqualTo("2025-01-02");
    }

    @Test
    void unAdminEditaNombresYFechaDeNacimientoDeUnClienteSinDocumento() {
        UUID admin = cuenta("admin@g7.test", null, "ADMIN_SISTEMA");
        UUID cliente = cuenta("c@clientes.test", null, "CLIENTE");

        PerfilProyectado p = perfil.actualizarComoAdministrador(actor(admin), cliente,
                new CambioAdministrativo(pr("Carla"), om(), om(), pr(LocalDate.parse("2000-05-17")), om(), om(),
                        om(), false), CONTEXTO);

        assertThat(p.nombres()).isEqualTo("Carla");
        assertThat(p.atributosCliente().fechaNacimiento()).isEqualTo(LocalDate.parse("2000-05-17"));
        assertThat(jdbc.queryForObject("select documento_cifrado is null from perfil_cliente where usuario_id = ?",
                Boolean.class, cliente)).as("la fila nueva respeta ck_perfil_cliente_documento").isTrue();
    }

    @Test
    void sinAtributosEditarNoSeEscribeNada() {
        UUID cliente = cuenta("c@clientes.test", null, "CLIENTE");
        UUID vendedor = cuenta("v@tienda.test", null, "VENDEDOR");

        assertThatThrownBy(() -> perfil.actualizarComoAdministrador(actor(cliente), vendedor,
                new CambioAdministrativo(om(), om(), om(), om(), pr("VEND-9"), om(), om(), false), CONTEXTO))
                .isInstanceOf(EdicionAdministrativaNoPermitidaException.class);

        assertThat(jdbc.queryForObject("select count(*) from perfil_vendedor", Integer.class)).isZero();
        assertThat(EVENTOS).isEmpty();
        assertThat(AUDITORIAS).isEmpty();
    }

    @Test
    void unAtributoDeVendedorEnUnClienteNoAplicaYNoCambiaLosBasicos() {
        UUID admin = cuenta("admin@g7.test", null, "ADMIN_SISTEMA");
        UUID cliente = cuenta("c@clientes.test", null, "CLIENTE");

        assertThatThrownBy(() -> perfil.actualizarComoAdministrador(actor(admin), cliente,
                new CambioAdministrativo(pr("Otro"), om(), om(), om(), pr("VEND-9"), om(), om(), false), CONTEXTO))
                .isInstanceOf(AtributoNoAplicableException.class);

        assertThat(jdbc.queryForObject("select nombres from usuario where id = ?", String.class, cliente))
                .isEqualTo("María");
        assertThat(EVENTOS).isEmpty();
    }
}
