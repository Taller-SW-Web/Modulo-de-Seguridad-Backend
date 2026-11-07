package com.marketplace.auth_service.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.AuditoriaJpaAdapter;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import com.marketplace.auth_service.configuration.AuditoriaConfig;
import com.marketplace.auth_service.configuration.AutorizacionConfig;
import com.marketplace.auth_service.configuration.PerfilConfig;
import com.marketplace.auth_service.configuration.UsuarioConfiguration;
import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.CampoEditable;
import com.marketplace.auth_service.perfil.application.port.in.PerfilPort;
import com.marketplace.auth_service.perfil.domain.model.AtributoInvalidoException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * EP-08-T007 (parte persistida): {@code PerfilPort.actualizarPropio} con proveedores reales
 * (cuentas, autorización, auditoría y outbox) sobre PostgreSQL. Comprueba el estado de la base,
 * no solo la respuesta: omitidos conservados, nada parcial ante un inválido, celular no verificado,
 * evento y auditoría con nombres de campo (jamás valores), rollback del outbox y serialización
 * por cuenta mediante el lock de la fila.
 *
 * <p>No cubre: firma/login reales (ver {@code PerfilPropioHttpPostgresTest}), publicación en RabbitMQ,
 * documento protegido (falta la clave de Christian) ni edición administrativa (H4).
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({UsuarioConfiguration.class, AutorizacionConfig.class, PerfilConfig.class, AuditoriaConfig.class,
        AuditoriaJpaAdapter.class, PerfilEdicionPostgresTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PerfilEdicionPostgresTest {

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

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) throws Exception {
        BasePerfilPostgres.registrar(registro, "perfil_edicion");
    }

    private static final String EVENTO = "usuario.atributos_actualizados";

    /** AuditoriaConfig pide un RabbitTemplate para el reintento; la porcion JPA no autoconfigura RabbitMQ. */
    @MockitoBean RabbitTemplate rabbit;

    @Autowired PerfilPort perfil;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;

    private final ExecutorService hilos = Executors.newFixedThreadPool(3);

    @BeforeEach
    void limpiar() {
        jdbc.execute("truncate usuario cascade");
        jdbc.execute("truncate outbox");
    }

    @AfterEach
    void cerrar() {
        hilos.shutdownNow();
    }

    private UUID cuenta(String correo, String celular, boolean celularVerificado) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, celular, celular_verificado, estado) "
                + "values (?, ?, 'María', 'Quispe Rojas', ?, ?, 'ACTIVO'::estado_cuenta)",
                id, correo, celular, celularVerificado);
        jdbc.update("insert into usuario_rol(usuario_id, rol_codigo) values (?, 'CLIENTE'::codigo_rol)", id);
        return id;
    }

    private static ActorUsuario actor(UUID sub) {
        return new ActorUsuario(sub, UUID.randomUUID(), Set.of(), Set.of());
    }

    private static CambioAtributos cambio(CampoEditable<String> n, CampoEditable<String> a, CampoEditable<String> c) {
        return new CambioAtributos(n, a, c);
    }

    private static CampoEditable<String> om() {
        return CampoEditable.omitido();
    }

    private static CampoEditable<String> pr(String v) {
        return CampoEditable.presente(v);
    }

    private Map<String, Object> fila(UUID id) {
        return jdbc.queryForMap("select nombres, apellidos, celular, celular_verificado from usuario where id = ?", id);
    }

    private List<Map<String, Object>> eventos() {
        return jdbc.queryForList("select topico, payload::text as payload, estado from outbox where topico = ?", EVENTO);
    }

    @Test
    void edicionParcialPersisteLoEnviadoYConservaLosOmitidos() {
        UUID ana = cuenta("ana@clientes.test", "+51987654321", true);

        perfil.actualizarPropio(actor(ana), cambio(pr("Eva Lucía"), om(), om()));

        Map<String, Object> fila = fila(ana);
        assertThat(fila.get("nombres")).isEqualTo("Eva Lucía");
        assertThat(fila.get("apellidos")).isEqualTo("Quispe Rojas");
        assertThat(fila.get("celular")).isEqualTo("+51987654321");
        assertThat(fila.get("celular_verificado")).isEqualTo(true);
    }

    @Test
    void celularCambiadoQuedaNoVerificadoEnLaBase() {
        UUID ana = cuenta("ana@clientes.test", "+51987654321", true);

        var respuesta = perfil.actualizarPropio(actor(ana), cambio(om(), om(), pr("+51900000000")));

        assertThat(fila(ana).get("celular")).isEqualTo("+51900000000");
        assertThat(fila(ana).get("celular_verificado")).isEqualTo(false);
        assertThat(respuesta.celularVerificado()).isFalse();
    }

    @Test
    void laEdicionDeUnaCuentaNoTocaALaOtra() {
        UUID ana = cuenta("ana@clientes.test", "+51987654321", true);
        UUID beto = cuenta("beto@clientes.test", "+51911111111", true);

        perfil.actualizarPropio(actor(ana), cambio(pr("Solo Ana"), om(), pr("+51900000000")));

        Map<String, Object> deBeto = fila(beto);
        assertThat(deBeto.get("nombres")).isEqualTo("María");
        assertThat(deBeto.get("celular")).isEqualTo("+51911111111");
        assertThat(deBeto.get("celular_verificado")).isEqualTo(true);
        assertThat(eventos()).hasSize(1);
        assertThat(eventos().get(0).get("payload").toString()).contains(ana.toString()).doesNotContain(beto.toString());
    }

    @Test
    void unInvalidoNoDejaCambiosParcialesNiEvento() {
        UUID ana = cuenta("ana@clientes.test", "+51987654321", true);

        assertThatThrownBy(() -> perfil.actualizarPropio(actor(ana), cambio(pr("Nombre Valido"), om(), pr("123"))))
                .isInstanceOf(AtributoInvalidoException.class);

        assertThat(fila(ana).get("nombres")).isEqualTo("María");
        assertThat(fila(ana).get("celular")).isEqualTo("+51987654321");
        assertThat(eventos()).isEmpty();
    }

    @Test
    void elEventoLlevaLosNombresDeCampoYNuncaLosValores() {
        UUID ana = cuenta("ana@clientes.test", "+51987654321", true);

        perfil.actualizarPropio(actor(ana), cambio(pr("ValorSecretoNombre"), om(), pr("+51900000099")));

        var evento = eventos();
        assertThat(evento).hasSize(1);
        assertThat(evento.get(0).get("estado")).isEqualTo("PENDIENTE");
        String payload = evento.get(0).get("payload").toString();
        assertThat(payload).contains(ana.toString()).contains("nombres").contains("celular")
                .doesNotContain("apellidos")
                .doesNotContain("ValorSecretoNombre").doesNotContain("+51900000099").doesNotContain("ana@clientes.test");
    }

    @Test
    void unaEdicionSinCambioRealNoPublicaNada() {
        UUID ana = cuenta("ana@clientes.test", "+51987654321", true);

        perfil.actualizarPropio(actor(ana), cambio(pr("María"), om(), om()));

        assertThat(eventos()).isEmpty();
        assertThat(fila(ana).get("celular_verificado")).isEqualTo(true);
    }

    @Test
    void laAuditoriaNoCriticaSeRegistraTrasElCommitSinValores() {
        UUID ana = cuenta("ana@clientes.test", "+51987654321", true);

        perfil.actualizarPropio(actor(ana), cambio(pr("ValorSecretoNombre"), om(), om()));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var filas = jdbc.queryForList("select accion, resultado, actor_id, objetivo_usuario_id::text as objetivo, "
                    + "detalle::text as detalle from auditoria_seguridad "
                    + "where accion = 'ATRIBUTOS_ACTUALIZADOS' and objetivo_usuario_id = ?", ana);
            assertThat(filas).hasSize(1);
            assertThat(filas.get(0).get("objetivo")).isEqualTo(ana.toString());
            assertThat(filas.get(0).get("actor_id")).isEqualTo(ana.toString());
            assertThat(filas.get(0).get("detalle").toString()).contains("nombres").doesNotContain("ValorSecretoNombre");
        });
    }

    @Test
    void siLaTransaccionRevierteDesaparecenLaEscrituraElEventoYLaAuditoria() {
        UUID ana = cuenta("ana@clientes.test", "+51987654321", true);

        assertThatThrownBy(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            perfil.actualizarPropio(actor(ana), cambio(pr("Revertido"), om(), om()));
            throw new IllegalStateException("fallo posterior en la misma transacción");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(fila(ana).get("nombres")).isEqualTo("María");
        assertThat(eventos()).as("rollback del outbox").isEmpty();
        // La auditoría no crítica se programa tras el commit: si no hubo commit, nunca se intenta.
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(jdbc.queryForObject(
                "select count(*) from auditoria_seguridad where accion = 'ATRIBUTOS_ACTUALIZADOS' "
                + "and objetivo_usuario_id = ?", Long.class, ana)).isZero();
    }

    @Test
    void unaMutacionConcurrenteEsperaElLockDeLaCuentaYLuegoSeAplica() throws Exception {
        UUID ana = cuenta("ana@clientes.test", "+51987654321", true);
        CountDownLatch bloqueada = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);

        Future<?> quienBloquea = hilos.submit(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            jdbc.queryForObject("select id from usuario where id = ? for update", UUID.class, ana);
            bloqueada.countDown();
            try {
                liberar.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(bloqueada.await(10, TimeUnit.SECONDS)).isTrue();

        Future<?> edicion = hilos.submit(() -> perfil.actualizarPropio(actor(ana), cambio(pr("Despues Del Lock"), om(), om())));
        Thread.sleep(700);
        assertThat(edicion.isDone()).as("la edición espera mientras otra transacción tiene la cuenta").isFalse();
        assertThat(fila(ana).get("nombres")).isEqualTo("María");

        liberar.countDown();
        quienBloquea.get(10, TimeUnit.SECONDS);
        edicion.get(10, TimeUnit.SECONDS);
        assertThat(fila(ana).get("nombres")).isEqualTo("Despues Del Lock");
    }

    @Test
    void dosEdicionesSimultaneasNoPierdenNingunCambioNiEvento() throws Exception {
        UUID ana = cuenta("ana@clientes.test", "+51987654321", true);
        CountDownLatch salida = new CountDownLatch(1);

        Future<?> nombres = hilos.submit(() -> {
            salida.await();
            return perfil.actualizarPropio(actor(ana), cambio(pr("Nombre Nuevo"), om(), om()));
        });
        Future<?> celular = hilos.submit(() -> {
            salida.await();
            return perfil.actualizarPropio(actor(ana), cambio(om(), om(), pr("+51900000001")));
        });
        salida.countDown();
        nombres.get(15, TimeUnit.SECONDS);
        celular.get(15, TimeUnit.SECONDS);

        Map<String, Object> fila = fila(ana);
        assertThat(fila.get("nombres")).isEqualTo("Nombre Nuevo");
        assertThat(fila.get("celular")).isEqualTo("+51900000001");
        assertThat(fila.get("celular_verificado")).isEqualTo(false);
        assertThat(eventos()).hasSize(2);
    }
}
