package com.marketplace.auth_service.identidad.adapter.out.persistence;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import com.marketplace.auth_service.configuration.AutorizacionConfig;
import com.marketplace.auth_service.identidad.application.dto.ClienteTecnico;
import com.marketplace.auth_service.identidad.application.dto.ResultadoGestionCliente;
import com.marketplace.auth_service.identidad.application.dto.SecretoEntregado;
import com.marketplace.auth_service.identidad.application.port.out.GobiernoClientesPort;
import com.marketplace.auth_service.identidad.application.service.GestionClientesTecnicosService;
import com.marketplace.auth_service.identidad.domain.model.ClienteServicio;
import com.marketplace.auth_service.identidad.domain.model.SecretoCliente;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SPEC-19 sobre el esquema observado de Supabase: la gestión escribe en las mismas
 * tablas que lee la emisión de tokens, cada operación es transaccional y los cambios
 * se ven en la siguiente emisión (ESC-19.1 a ESC-19.7 y ESC-19.10).
 * Cada clase crea su propia base desechable; nunca toca Supabase.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"},
        showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({AutorizacionConfig.class, ClientesTecnicosPostgresTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClientesTecnicosPostgresTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository")
    static class Persistencia { }

    static final ActorUsuario ADMIN = new ActorUsuario(UUID.randomUUID(), UUID.randomUUID(),
            Set.of("ADMIN_SISTEMA"), Set.of("integracion.gestionar"));
    static final ContextoPeticion CONTEXTO = new ContextoPeticion("10.0.0.1", "prueba", UUID.randomUUID());

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
        String nombre = "tecnicos_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        try (var admin = DriverManager.getConnection(url, usuario, contrasena); var s = admin.createStatement()) {
            s.execute("CREATE DATABASE " + nombre);
        }
        String propia = url.replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
        try (var c = DriverManager.getConnection(propia, usuario, contrasena); var s = c.createStatement()) {
            s.execute(leer("mfa/base-observada/schema.sql"));
            s.execute(leer("db/autorizacion/R__catalogo_autorizacion.sql"));
            s.execute(leer("db/identidad/R__catalogo_scopes.sql"));
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

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;

    Gobierno gobierno;
    GestionClientesTecnicosService servicio;
    ClientesServicioJdbcAdapter emision;
    TransactionTemplate tx;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate cliente_servicio_scope, cliente_servicio");
        gobierno = new Gobierno();
        servicio = new GestionClientesTecnicosService(new RegistroClientesJdbcAdapter(jdbc), gobierno, Clock.systemUTC());
        emision = new ClientesServicioJdbcAdapter(jdbc);
        tx = new TransactionTemplate(transacciones);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private <T> T enTx(Supplier<T> operacion) {
        return tx.execute(estado -> operacion.get());
    }

    private static <T> T valor(ResultadoGestionCliente<T> resultado) {
        assertThat(resultado).isInstanceOf(ResultadoGestionCliente.Correcto.class);
        return ((ResultadoGestionCliente.Correcto<T>) resultado).valor();
    }

    @Test
    void esc19_1_elAltaQuedaListaParaEmitirConSuSecretoYAudiencias() {
        SecretoEntregado alta = valor(enTx(() -> servicio.crear(ADMIN, "modulo-prueba-h5", "Cliente de prueba H5",
                List.of("usuarios:leer", "cotizaciones:calcular"), CONTEXTO)));

        ClienteServicio emitible = emision.buscar("modulo-prueba-h5").orElseThrow();
        assertThat(emitible.activo()).isTrue();
        assertThat(SecretoCliente.coincide(alta.clientSecret(), emitible.secretoHash())).isTrue();
        assertThat(emitible.codigosScopes()).containsExactly("cotizaciones:calcular", "usuarios:leer");
        assertThat(emitible.audiencias()).containsExactly("api-despacho", "api-seguridad");
        assertThat(jdbc.queryForObject("select client_secret_hash from cliente_servicio where client_id = ?",
                String.class, "modulo-prueba-h5")).hasSize(64).isNotEqualTo(alta.clientSecret());
        assertThat(alta.cliente().scopes()).hasSize(2);
        assertThat(gobierno.acciones).containsExactly("CLIENTE_SERVICIO_CREADO");
    }

    @Test
    void esc19_2_y_3_duplicadoYScopeFueraDelCatalogoNoEscribenNada() {
        valor(enTx(() -> servicio.crear(ADMIN, "modulo-marketplace", "G1", List.of(), CONTEXTO)));
        String hash = jdbc.queryForObject("select client_secret_hash from cliente_servicio", String.class);

        var duplicado = enTx(() -> servicio.crear(ADMIN, "modulo-marketplace", "Otro", List.of(), CONTEXTO));
        var fuera = enTx(() -> servicio.crear(ADMIN, "modulo-nuevo", "Nuevo",
                List.of("usuarios:leer", "pagos:cobrar"), CONTEXTO));

        assertThat(duplicado).isEqualTo(ResultadoGestionCliente.rechazo("CLIENTE_SERVICIO_EXISTENTE"));
        assertThat(fuera).isEqualTo(ResultadoGestionCliente.invalido("scopes"));
        assertThat(jdbc.queryForObject("select client_secret_hash from cliente_servicio", String.class)).isEqualTo(hash);
        assertThat(jdbc.queryForObject("select count(*) from cliente_servicio", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from cliente_servicio_scope", Integer.class)).isZero();
    }

    @Test
    void esc19_4_concederYRevocarCambianLaSiguienteEmision() {
        valor(enTx(() -> servicio.crear(ADMIN, "modulo-prueba", "Prueba", List.of("usuarios:leer"), CONTEXTO)));

        enTx(() -> servicio.conceder(ADMIN, "modulo-prueba", "roles:leer", CONTEXTO));
        assertThat(emision.buscar("modulo-prueba").orElseThrow().codigosScopes())
                .containsExactly("roles:leer", "usuarios:leer");

        enTx(() -> servicio.revocar(ADMIN, "modulo-prueba", "roles:leer", CONTEXTO));
        assertThat(emision.buscar("modulo-prueba").orElseThrow().codigosScopes()).containsExactly("usuarios:leer");
        assertThat(gobierno.acciones).containsExactly("CLIENTE_SERVICIO_CREADO", "SCOPE_CONCEDIDO", "SCOPE_REVOCADO");
    }

    @Test
    void esc19_5_dosConcesionesSimultaneasDejanUnaFilaYUnaAuditoria() throws Exception {
        valor(enTx(() -> servicio.crear(ADMIN, "modulo-prueba", "Prueba", List.of(), CONTEXTO)));
        CountDownLatch salida = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            List<Future<ResultadoGestionCliente<Void>>> resultados = List.of(
                    hilos.submit(() -> { salida.await(); return enTx(() -> servicio.conceder(ADMIN, "modulo-prueba", "roles:leer", CONTEXTO)); }),
                    hilos.submit(() -> { salida.await(); return enTx(() -> servicio.conceder(ADMIN, "modulo-prueba", "roles:leer", CONTEXTO)); }));
            salida.countDown();
            for (var resultado : resultados) {
                assertThat(resultado.get()).isInstanceOf(ResultadoGestionCliente.Correcto.class);
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from cliente_servicio_scope", Integer.class)).isEqualTo(1);
        assertThat(gobierno.acciones.stream().filter("SCOPE_CONCEDIDO"::equals)).hasSize(1);
    }

    @Test
    void esc19_6_rotarInvalidaElSecretoAnteriorAlInstante() {
        SecretoEntregado alta = valor(enTx(() -> servicio.crear(ADMIN, "modulo-prueba", "Prueba", List.of(), CONTEXTO)));

        SecretoEntregado rotado = valor(enTx(() -> servicio.rotarSecreto(ADMIN, "modulo-prueba", CONTEXTO)));

        String hash = emision.buscar("modulo-prueba").orElseThrow().secretoHash();
        assertThat(SecretoCliente.coincide(alta.clientSecret(), hash)).isFalse();
        assertThat(SecretoCliente.coincide(rotado.clientSecret(), hash)).isTrue();
    }

    @Test
    void esc19_7_desactivarImpideEmitirYReactivarConservaLosScopes() {
        valor(enTx(() -> servicio.crear(ADMIN, "modulo-prueba", "Prueba", List.of("roles:leer"), CONTEXTO)));

        enTx(() -> servicio.cambiarEstado(ADMIN, "modulo-prueba", false, CONTEXTO));
        assertThat(emision.buscar("modulo-prueba").orElseThrow().activo()).isFalse();

        enTx(() -> servicio.cambiarEstado(ADMIN, "modulo-prueba", true, CONTEXTO));
        ClienteServicio reactivado = emision.buscar("modulo-prueba").orElseThrow();
        assertThat(reactivado.activo()).isTrue();
        assertThat(reactivado.codigosScopes()).containsExactly("roles:leer");
    }

    @Test
    void esc19_10_siLaAuditoriaFallaNadaCambia() {
        SecretoEntregado alta = valor(enTx(() -> servicio.crear(ADMIN, "modulo-prueba", "Prueba", List.of(), CONTEXTO)));
        gobierno.fallar = true;

        assertThatThrownBy(() -> enTx(() -> servicio.rotarSecreto(ADMIN, "modulo-prueba", CONTEXTO)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> enTx(() -> servicio.conceder(ADMIN, "modulo-prueba", "roles:leer", CONTEXTO)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> enTx(() -> servicio.crear(ADMIN, "modulo-otro", "Otro", List.of(), CONTEXTO)))
                .isInstanceOf(IllegalStateException.class);

        ClienteServicio intacto = emision.buscar("modulo-prueba").orElseThrow();
        assertThat(SecretoCliente.coincide(alta.clientSecret(), intacto.secretoHash())).isTrue();
        assertThat(intacto.scopes()).isEmpty();
        assertThat(emision.buscar("modulo-otro")).isEmpty();
    }

    @Test
    void esc19_9_elListadoYElCatalogoSalenDeLaBaseSinHash() {
        valor(enTx(() -> servicio.crear(ADMIN, "modulo-b", "B", List.of("roles:leer"), CONTEXTO)));
        valor(enTx(() -> servicio.crear(ADMIN, "modulo-a", "A", List.of(), CONTEXTO)));
        String hash = jdbc.queryForObject("select client_secret_hash from cliente_servicio where client_id = 'modulo-b'",
                String.class);

        List<ClienteTecnico> lista = valor(enTx(() -> servicio.listar(ADMIN, CONTEXTO)));

        assertThat(lista).extracting(ClienteTecnico::clientId).containsExactly("modulo-a", "modulo-b");
        assertThat(lista.get(0).scopes()).isEmpty();
        assertThat(lista.toString()).doesNotContain(hash);
        assertThat(valor(enTx(() -> servicio.catalogo(ADMIN, CONTEXTO)))).hasSize(28);
    }

    static final class Gobierno implements GobiernoClientesPort {
        volatile boolean fallar;
        final List<String> acciones = new CopyOnWriteArrayList<>();

        @Override public boolean autorizado(ActorUsuario actor) { return true; }

        @Override public void registrar(String accion, ActorUsuario actor, ContextoPeticion contexto, Instant fecha,
                                        Map<String, String> detalle) {
            if (fallar) {
                throw new IllegalStateException("auditoría caída");
            }
            acciones.add(accion);
        }

        @Override public void denegado(ActorUsuario actor, ContextoPeticion contexto, Instant fecha) { }
    }
}
