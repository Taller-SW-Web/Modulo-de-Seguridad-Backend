package com.marketplace.auth_service.credencial.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.TokenUnUsoJpaRepository;
import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.service.TokensUnUsoService;
import com.marketplace.auth_service.credencial.domain.model.TokenUnUso;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
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
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TokenUnUsoJpaPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6");

    @DynamicPropertySource static void postgres(DynamicPropertyRegistry r) {
        POSTGRES.start();
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var input = new ClassPathResource("mfa/base-observada/schema.sql").getInputStream();
                 var statement = c.createStatement()) {
                statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            ScriptUtils.executeSqlScript(c, new FileSystemResource("docs/migraciones-propuestas/V101__credencial_version.sql"));
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
    private TokensUnUsoService service;
    private UUID usuarioId;

    @BeforeEach void preparar() {
        tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        service = new TokensUnUsoService(new TokenUnUsoJpaAdapter(repository), Clock.systemUTC(), new java.security.SecureRandom());
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

    private boolean consumirTras(CountDownLatch start, UUID id) throws Exception {
        start.await();
        UUID revision = repository.findById(id).orElseThrow().getContextoId();
        Boolean result = tx.execute(s -> service.consumir(id, usuarioId, PropositoToken.DESBLOQUEO_AUTOMATICO, revision, Instant.now()));
        return Boolean.TRUE.equals(result);
    }
}
