package com.marketplace.auth_service.credencial.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.TokenUnUsoJpaRepository;
import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.TokenUnUsoJpaEntity;
import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.credencial.application.port.out.AuditoriaRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.CorreoRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.CuentasRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.CuotaRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.HashCorreoSolicitudPort;
import com.marketplace.auth_service.credencial.application.dto.CuentaRecuperacion;
import com.marketplace.auth_service.credencial.application.dto.ResultadoSolicitudRecuperacion;
import com.marketplace.auth_service.credencial.application.service.SolicitudRecuperacionService;
import com.marketplace.auth_service.credencial.adapter.in.transaction.CorreoRecuperacionTransaccional;
import com.marketplace.auth_service.credencial.adapter.out.capacidad.CorreoRecuperacionAdapter;
import com.marketplace.auth_service.credencial.adapter.out.crypto.HmacCorreoSolicitudJca;
import com.marketplace.auth_service.credencial.adapter.out.persistence.SolicitudesRecuperacionPostgresAdapter;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.configuration.TokensUnUsoConfig;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.marketplace.auth_service.credencial.domain.model.TokenUnUso;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TokenUnUsoJpaPostgresTest.PersistenciaToken.class, TokensUnUsoConfig.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TokenUnUsoJpaPostgresTest {
    private static final UUID USUARIO_TOKEN_HISTORICO = UUID.fromString("802e8a8b-91f8-4e98-9f00-000000000105");
    private static final UUID ID_TOKEN_HISTORICO = UUID.fromString("802e8a8b-91f8-4e98-9f00-000000000106");
    private static final String HASH_TOKEN_HISTORICO = "1050000000000000000000000000000000000000000000000000000000000000";
    /** Aísla esta prueba del escaneo de entidades y repositorios de otras capacidades. */
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = TokenUnUsoJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = TokenUnUsoJpaRepository.class)
    static class PersistenciaToken {
        @org.springframework.context.annotation.Bean HashCorreoSolicitudPort hashCorreoSolicitudPort() {
            return new HmacCorreoSolicitudJca("clave-test-recuperacion-32-bytes-minimo".getBytes(StandardCharsets.UTF_8));
        }
        @org.springframework.context.annotation.Bean CuotaRecuperacionPort cuotaRecuperacionPort(
                JdbcTemplate jdbc, HashCorreoSolicitudPort hash) {
            return new SolicitudesRecuperacionPostgresAdapter(jdbc, hash);
        }
    }

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6");

    @DynamicPropertySource static void postgres(DynamicPropertyRegistry r) {
        POSTGRES.start();
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var input = new ClassPathResource("mfa/base-observada/schema.sql").getInputStream();
                 var statement = c.createStatement()) {
                statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            ScriptUtils.executeSqlScript(c, new FileSystemResource("docs/migraciones-propuestas/V101__credencial_version.sql"));
            try (var seed = c.createStatement()) {
                seed.execute("insert into usuario(id, correo, nombres, apellidos, estado) values "
                        + "('" + USUARIO_TOKEN_HISTORICO + "', 'historico-t004@example.test', 'Token', 'Historico', 'ACTIVO')");
                seed.execute("insert into token_un_uso(id, proposito, usuario_id, hash_token, destino, expiracion, usado, creado_en) "
                        + "values ('" + ID_TOKEN_HISTORICO + "', 'DESBLOQUEO'::proposito_token, '" + USUARIO_TOKEN_HISTORICO
                        + "', '" + HASH_TOKEN_HISTORICO + "', null, timestamptz '2026-10-15 12:30:00+00', false, "
                        + "timestamptz '2026-10-15 12:00:00+00')");
            }
            ScriptUtils.executeSqlScript(c, new FileSystemResource("docs/migraciones-propuestas/V105__token_un_uso_contexto_id.sql"));
        } catch (Exception e) { throw new IllegalStateException("No se pudo preparar PostgreSQL aislado para token_un_uso", e); }
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TokenUnUsoJpaRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private TransactionTemplate tx;
    @Autowired TokensUnUsoPort service;
    @Autowired CuotaRecuperacionPort cuota;
    @Autowired EntityManager entityManager;
    private UUID usuarioId;

    @BeforeEach void preparar() {
        tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        usuarioId = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?, ?, 'Token', 'T004', 'ACTIVO')",
                usuarioId, usuarioId + "@example.test");
    }

    @Test void v105YMappingGuardanRevisionHashYConsumenUnaVez() {
        UUID revision = UUID.randomUUID();
        var emitido = tx.execute(s -> service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Duration.ofMinutes(30)));
        var fila = repository.findById(emitido.id()).orElseThrow();
        assertThat(fila.getContextoId()).isEqualTo(revision);
        assertThat(fila.getHashToken()).hasSize(64).isNotEqualTo(emitido.tokenClaro());
        assertThat(fila.getExpiraEn().toInstant()).isEqualTo(emitido.expiraEn());
        assertThat(tx.execute(s -> service.localizar(fila.getHashToken(), PropositoToken.DESBLOQUEO_AUTOMATICO))
                .orElseThrow().contextoId()).isEqualTo(revision);
        Boolean primera = tx.execute(s -> Boolean.valueOf(service.consumir(emitido.id(), usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Instant.now())));
        Boolean segunda = tx.execute(s -> Boolean.valueOf(service.consumir(emitido.id(), usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Instant.now())));
        assertThat(primera).isTrue(); assertThat(segunda).isFalse();
    }

    @Test void propositosDeVerificacionRecuperacionYCambioCorreoSePersistenAislados() {
        String correoNuevo = "nuevo-" + usuarioId + "@example.test";
        var verificacion = tx.execute(s -> service.emitir(usuarioId, PropositoToken.VERIFICAR_CORREO,
                Duration.ofHours(24)));
        var recuperacion = tx.execute(s -> service.emitir(usuarioId, PropositoToken.RECUPERACION,
                Duration.ofMinutes(30)));
        var cambioCorreo = tx.execute(s -> service.emitirCambioCorreo(usuarioId, correoNuevo));

        var filaVerificacion = repository.findById(verificacion.id()).orElseThrow();
        var filaRecuperacion = repository.findById(recuperacion.id()).orElseThrow();
        var filaCambioCorreo = repository.findById(cambioCorreo.id()).orElseThrow();
        assertThat(filaVerificacion.getProposito().name()).isEqualTo("VERIFICAR_CORREO");
        assertThat(filaRecuperacion.getProposito().name()).isEqualTo("RECUPERACION");
        assertThat(filaCambioCorreo.getProposito().name()).isEqualTo("CAMBIO_CORREO");
        assertThat(filaVerificacion.getDestino()).isNull();
        assertThat(filaRecuperacion.getDestino()).isNull();
        assertThat(filaCambioCorreo.getDestino()).isEqualTo(correoNuevo);
        assertThat(filaVerificacion.getHashToken()).hasSize(64).isNotEqualTo(verificacion.tokenClaro());
        assertThat(filaRecuperacion.getHashToken()).hasSize(64).isNotEqualTo(recuperacion.tokenClaro());
        assertThat(filaCambioCorreo.getHashToken()).hasSize(64).isNotEqualTo(cambioCorreo.tokenClaro());
        assertThat(filaVerificacion.getExpiraEn().toInstant()).isEqualTo(verificacion.expiraEn());
        assertThat(filaRecuperacion.getExpiraEn().toInstant()).isEqualTo(recuperacion.expiraEn());
        assertThat(filaCambioCorreo.getExpiraEn().toInstant()).isEqualTo(cambioCorreo.expiraEn());

        var halladoCambio = service.localizar(filaCambioCorreo.getHashToken(), PropositoToken.CAMBIO_CORREO)
                .orElseThrow();
        assertThat(halladoCambio.destino()).isEqualTo(correoNuevo);
        assertThat(halladoCambio.toString()).doesNotContain(correoNuevo);
        assertThat(service.localizar(filaCambioCorreo.getHashToken(), PropositoToken.RECUPERACION)).isEmpty();
        Boolean propositoIncorrecto = tx.execute(s -> Boolean.valueOf(service.consumir(verificacion.id(),
                usuarioId, PropositoToken.RECUPERACION, Instant.now())));
        assertThat(propositoIncorrecto).isFalse();
        Boolean primeraVerificacion = tx.execute(s -> Boolean.valueOf(service.consumir(verificacion.id(),
                usuarioId, PropositoToken.VERIFICAR_CORREO, Instant.now())));
        Boolean segundaVerificacion = tx.execute(s -> Boolean.valueOf(service.consumir(verificacion.id(),
                usuarioId, PropositoToken.VERIFICAR_CORREO, Instant.now())));
        Boolean primeraRecuperacion = tx.execute(s -> Boolean.valueOf(service.consumir(recuperacion.id(),
                usuarioId, PropositoToken.RECUPERACION, Instant.now())));
        Boolean primerCambioCorreo = tx.execute(s -> Boolean.valueOf(service.consumir(cambioCorreo.id(),
                usuarioId, PropositoToken.CAMBIO_CORREO, Instant.now())));
        assertThat(primeraVerificacion).isTrue();
        assertThat(segundaVerificacion).isFalse();
        assertThat(primeraRecuperacion).isTrue();
        assertThat(primerCambioCorreo).isTrue();
    }

    @Test void reemisionInvalidaSoloTokensDelMismoProposito() {
        var verificacionAnterior = tx.execute(s -> service.emitir(usuarioId, PropositoToken.VERIFICAR_CORREO,
                Duration.ofHours(24)));
        var recuperacionAnterior = tx.execute(s -> service.emitir(usuarioId, PropositoToken.RECUPERACION,
                Duration.ofMinutes(30)));
        var cambioAnterior = tx.execute(s -> service.emitirCambioCorreo(usuarioId, "anterior@example.test"));

        var verificacionNueva = tx.execute(s -> service.emitir(usuarioId, PropositoToken.VERIFICAR_CORREO,
                Duration.ofHours(24)));
        assertThat(repository.findById(verificacionAnterior.id()).orElseThrow().isUsado()).isTrue();
        assertThat(repository.findById(recuperacionAnterior.id()).orElseThrow().isUsado()).isFalse();
        assertThat(repository.findById(cambioAnterior.id()).orElseThrow().isUsado()).isFalse();

        var recuperacionNueva = tx.execute(s -> service.emitir(usuarioId, PropositoToken.RECUPERACION,
                Duration.ofMinutes(30)));
        assertThat(repository.findById(recuperacionAnterior.id()).orElseThrow().isUsado()).isTrue();
        assertThat(repository.findById(verificacionNueva.id()).orElseThrow().isUsado()).isFalse();
        assertThat(repository.findById(cambioAnterior.id()).orElseThrow().isUsado()).isFalse();

        var cambioNuevo = tx.execute(s -> service.emitirCambioCorreo(usuarioId, "nuevo@example.test"));
        assertThat(repository.findById(cambioAnterior.id()).orElseThrow().isUsado()).isTrue();
        assertThat(repository.findById(verificacionNueva.id()).orElseThrow().isUsado()).isFalse();
        assertThat(repository.findById(recuperacionNueva.id()).orElseThrow().isUsado()).isFalse();
        assertThat(repository.findById(cambioNuevo.id()).orElseThrow().getDestino()).isEqualTo("nuevo@example.test");
    }

    @Test void tokenDesbloqueoHistoricoSinContextoSeRechazaComoNoEncontrado() {
        assertThat(repository.findById(ID_TOKEN_HISTORICO).orElseThrow().getContextoId()).isNull();
        var localizado = tx.execute(s -> service.localizar(HASH_TOKEN_HISTORICO,
                PropositoToken.DESBLOQUEO_AUTOMATICO));
        assertThat(localizado).isEmpty();
        Boolean consumido = tx.execute(s -> Boolean.valueOf(service.consumir(ID_TOKEN_HISTORICO,
                USUARIO_TOKEN_HISTORICO, PropositoToken.DESBLOQUEO_AUTOMATICO, Instant.now())));
        assertThat(consumido).isFalse();
    }

    @Test void rollbackNoConsumeYDosConsumosConcurrentesSoloConfirmanUno() throws Exception {
        var rollbackToken = tx.execute(s -> service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, UUID.randomUUID(), Duration.ofMinutes(30)));
        UUID revision = repository.findById(rollbackToken.id()).orElseThrow().getContextoId();
        tx.execute(s -> { assertThat(service.consumir(rollbackToken.id(), usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Instant.now())).isTrue();
            s.setRollbackOnly(); return null; });
        Boolean trasRollback = tx.execute(s -> Boolean.valueOf(service.consumir(rollbackToken.id(), usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Instant.now())));
        assertThat(trasRollback).isTrue();

        var concurrent = tx.execute(s -> service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, UUID.randomUUID(), Duration.ofMinutes(30)));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> consumirTras(start, concurrent.id()));
            var b = pool.submit(() -> consumirTras(start, concurrent.id()));
            start.countDown();
            int exitosos = (Boolean.TRUE.equals(a.get()) ? 1 : 0) + (Boolean.TRUE.equals(b.get()) ? 1 : 0);
            assertThat(exitosos).isEqualTo(1);
        }
    }

    @Test void lecturaPreviaNoPermiteDobleConsumoNiUsarUnEnlaceReemplazado() throws Exception {
        assertLecturaPreviaNoPermiteSegundoConsumo(false);
        assertLecturaPreviaNoPermiteSegundoConsumo(true);
    }

    private void assertLecturaPreviaNoPermiteSegundoConsumo(boolean reemplazar) throws Exception {
        var emitido = tx.execute(s -> service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO,
                UUID.randomUUID(), Duration.ofMinutes(30)));
        String hash = repository.findById(emitido.id()).orElseThrow().getHashToken();
        CountDownLatch lecturaRealizada = new CountDownLatch(1);
        CountDownLatch permitirContinuar = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var consumoTardio = pool.submit(() -> tx.execute(s -> {
                assertThat(service.localizar(hash, PropositoToken.DESBLOQUEO_AUTOMATICO)).isPresent();
                lecturaRealizada.countDown();
                try {
                    if (!permitirContinuar.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("No llegó la señal para continuar la transacción");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return Boolean.valueOf(service.consumir(emitido.id(), usuarioId,
                        PropositoToken.DESBLOQUEO_AUTOMATICO,
                        repository.findById(emitido.id()).orElseThrow().getContextoId(), Instant.now()));
            }));
            assertThat(lecturaRealizada.await(10, TimeUnit.SECONDS)).isTrue();
            if (reemplazar) {
                tx.execute(s -> service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO,
                        UUID.randomUUID(), Duration.ofMinutes(30)));
            } else {
                UUID revision = repository.findById(emitido.id()).orElseThrow().getContextoId();
                Boolean primerConsumo = tx.execute(s -> Boolean.valueOf(service.consumir(emitido.id(), usuarioId,
                        PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Instant.now())));
                assertThat(primerConsumo).isTrue();
            }
            permitirContinuar.countDown();
            assertThat(consumoTardio.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            permitirContinuar.countDown();
        }
    }

    @Test void nuevaEmisionInvalidaSoloTokensVigentesDelMismoUsuarioYProposito() {
        var first = tx.execute(s -> service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, UUID.randomUUID(), Duration.ofMinutes(30)));
        tx.execute(s -> {
            service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, UUID.randomUUID(), Duration.ofMinutes(30));
            s.setRollbackOnly(); return null;
        });
        assertThat(repository.findById(first.id()).orElseThrow().isUsado()).isFalse();
        var otherUser = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?, ?, 'Otro', 'T004', 'ACTIVO')",
                otherUser, otherUser + "@example.test");
        var independent = tx.execute(s -> service.emitir(otherUser, PropositoToken.DESBLOQUEO_AUTOMATICO, UUID.randomUUID(), Duration.ofMinutes(30)));
        var second = tx.execute(s -> service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, UUID.randomUUID(), Duration.ofMinutes(30)));
        assertThat(repository.findById(first.id()).orElseThrow().isUsado()).isTrue();
        assertThat(repository.findById(independent.id()).orElseThrow().isUsado()).isFalse();
        assertThat(repository.findById(second.id()).orElseThrow().isUsado()).isFalse();
    }

    @Test void localizaAntesDeLocksSinAbrirEscriturasYMutacionesExigenProductor() {
        UUID revision = UUID.randomUUID();
        var emitido = tx.execute(s -> service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO,
                revision, Duration.ofMinutes(30)));
        String hash = repository.findById(emitido.id()).orElseThrow().getHashToken();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(service.localizar(hash, PropositoToken.DESBLOQUEO_AUTOMATICO).orElseThrow().contextoId()).isEqualTo(revision);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(repository.findById(emitido.id()).orElseThrow().isUsado()).isFalse();
        assertThatThrownBy(() -> service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> service.consumir(emitido.id(), usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> service.invalidarPrevios(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test void falloDeOutboxEsNeutroMantieneCuotaYRevierteTokenSinInvalidarElAnterior() {
        jdbc.execute("truncate table solicitud_limitada restart identity");
        var conCuenta = ejecutarSolicitudesTrasFallo("existente-" + UUID.randomUUID() + "@example.test", true);
        var sinCuenta = ejecutarSolicitudesTrasFallo("inexistente-" + UUID.randomUUID() + "@example.test", false);

        assertThat(conCuenta).containsExactly(new ResultadoSolicitudRecuperacion.Aceptada(),
                new ResultadoSolicitudRecuperacion.Aceptada(), new ResultadoSolicitudRecuperacion.Aceptada(),
                new ResultadoSolicitudRecuperacion.Limite(3600));
        assertThat(sinCuenta).isEqualTo(conCuenta);
    }

    private java.util.List<ResultadoSolicitudRecuperacion> ejecutarSolicitudesTrasFallo(String correo, boolean existe) {
        jdbc.execute("truncate table solicitud_limitada restart identity");
        UUID idUsuario = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?, ?, 'Recuperacion', 'Test', 'ACTIVO')",
                idUsuario, idUsuario + "@example.test");
        var cuenta = new CuentaRecuperacion(idUsuario, correo, true);
        var anterior = tx.execute(s -> service.emitir(idUsuario, PropositoToken.RECUPERACION, Duration.ofMinutes(30)));
        MensajeriaPort mensajeria = new MensajeriaPort() {
            @Override public UUID guardarEvento(com.marketplace.auth_service.auditoria.application.dto.EventoUsuario evento) {
                throw new UnsupportedOperationException("No se esperaba un evento");
            }
            @Override public UUID guardarCorreo(com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo entrega) {
                throw new IllegalStateException("Fallo de outbox inducido para la prueba");
            }
        };
        CorreoRecuperacionPort correoPort = new CorreoRecuperacionTransaccional(
                new CorreoRecuperacionAdapter(service, mensajeria, "https://spa.example.test"), manager);
        CuentasRecuperacionPort cuentas = solicitado -> existe ? Optional.of(cuenta) : Optional.empty();
        AuditoriaRecuperacionPort auditoria = (usuario, aceptada, contexto, ahora) -> { };
        var solicitud = new SolicitudRecuperacionService(cuentas, cuota, correoPort, auditoria,
                Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC));
        var contexto = new ContextoPeticion("127.0.0.1", "test", UUID.randomUUID());
        var resultados = new java.util.ArrayList<ResultadoSolicitudRecuperacion>();

        for (int i = 0; i < 4; i++) resultados.add(solicitud.solicitar(correo,
                com.marketplace.auth_service.credencial.application.dto.CanalOrigenRecuperacion.WEB, contexto));

        if (existe) {
            assertThat(repository.findById(anterior.id()).orElseThrow().isUsado()).isFalse();
            assertThat(repository.findAll().stream().filter(token -> token.getUsuarioId().equals(idUsuario)).toList())
                    .hasSize(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from solicitud_limitada", Long.class)).isEqualTo(3L);
        return resultados;
    }

    @Test void rechazoDeUsuarioPropositoRevisionYVencimientoNoConsume() {
        UUID revision = UUID.randomUUID();
        var emitido = tx.execute(s -> service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO,
                revision, Duration.ofMinutes(30)));
        tx.executeWithoutResult(s -> {
            assertThat(service.consumir(emitido.id(), UUID.randomUUID(), PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Instant.now())).isFalse();
            assertThat(service.consumir(emitido.id(), usuarioId, PropositoToken.RECUPERACION, revision, Instant.now())).isFalse();
            assertThat(service.consumir(emitido.id(), usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, UUID.randomUUID(), Instant.now())).isFalse();
            assertThat(service.consumir(emitido.id(), usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, emitido.expiraEn())).isFalse();
        });
        assertThat(repository.findById(emitido.id()).orElseThrow().isUsado()).isFalse();
    }

    @Test void invalidarYConsumirReleenEstadoSinDesconectarEntidadesDelProductor() {
        UUID revision = UUID.randomUUID();
        var primero = tx.execute(s -> service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO,
                revision, Duration.ofMinutes(30)));
        var independiente = tx.execute(s -> service.emitir(usuarioId, PropositoToken.RECUPERACION, Duration.ofMinutes(30)));
        tx.executeWithoutResult(s -> {
            var retenida = entityManager.find(TokenUnUsoJpaEntity.class, independiente.id());
            String hash = repository.findById(primero.id()).orElseThrow().getHashToken();
            assertThat(service.localizar(hash, PropositoToken.DESBLOQUEO_AUTOMATICO).orElseThrow().usado()).isFalse();
            service.invalidarPrevios(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO);
            assertThat(entityManager.contains(retenida)).isTrue();
            assertThat(service.localizar(hash, PropositoToken.DESBLOQUEO_AUTOMATICO).orElseThrow().usado()).isTrue();
            assertThat(service.consumir(primero.id(), usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Instant.now())).isFalse();
            var siguiente = service.emitir(usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, UUID.randomUUID(), Duration.ofMinutes(30));
            UUID nuevaRevision = repository.findById(siguiente.id()).orElseThrow().getContextoId();
            assertThat(service.consumir(siguiente.id(), usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, nuevaRevision, Instant.now())).isTrue();
            assertThat(entityManager.contains(retenida)).isTrue();
        });
        assertThat(repository.findById(independiente.id()).orElseThrow().isUsado()).isFalse();
    }

    private boolean consumirTras(CountDownLatch start, UUID id) throws Exception {
        start.await();
        UUID revision = repository.findById(id).orElseThrow().getContextoId();
        Boolean result = tx.execute(s -> service.consumir(id, usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Instant.now()));
        return Boolean.TRUE.equals(result);
    }
}
