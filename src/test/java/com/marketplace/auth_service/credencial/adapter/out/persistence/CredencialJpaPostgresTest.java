package com.marketplace.auth_service.credencial.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.CredencialJpaEntity;
import com.marketplace.auth_service.credencial.adapter.out.persistence.mapper.CredencialPersistenceMapper;
import com.marketplace.auth_service.credencial.adapter.out.persistence.mapper.PasswordHistorialPersistenceMapper;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.CredencialJpaRepository;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.PasswordHistorialJpaRepository;
import com.marketplace.auth_service.credencial.domain.model.AlgoritmoHash;
import com.marketplace.auth_service.credencial.domain.model.Credencial;
import com.marketplace.auth_service.credencial.domain.model.HashContrasena;
import com.marketplace.auth_service.credencial.domain.model.PasswordHistorial;
import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CredencialJpaPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6");

    @DynamicPropertySource
    static void configurarPostgres(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var schema = new ClassPathResource("mfa/base-observada/schema.sql").getInputStream();
                    var statement = connection.createStatement()) {
                statement.execute(new String(schema.readAllBytes(), StandardCharsets.UTF_8));
            }
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource("docs/migraciones-propuestas/V101__credencial_version.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource("docs/migraciones-propuestas/V105__token_un_uso_contexto_id.sql"));
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo preparar PostgreSQL aislado para credenciales", exception);
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired CredencialJpaRepository credenciales;
    @Autowired PasswordHistorialJpaRepository historiales;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private UUID usuarioId;

    @BeforeEach
    void prepararUsuario() {
        usuarioId = UUID.randomUUID();
        jdbc.update("""
                insert into usuario(id, correo, nombres, apellidos, estado)
                values (?, ?, 'Fixture', 'T001', 'ACTIVO')
                """, usuarioId, usuarioId + "@example.test");
    }

    @Test
    void appliesProposedMigrationAgainstObservedSchemaAndRoundTripsCredentialAndHistory() {
        String currentHash = "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA";
        Instant currentAt = Instant.parse("2026-10-14T16:00:00Z");
        Credencial current = new Credencial(
                usuarioId,
                usuarioId,
                new HashContrasena(currentHash, AlgoritmoHash.ARGON2ID),
                currentAt,
                0);
        CredencialJpaEntity saved = credenciales.saveAndFlush(CredencialPersistenceMapper.aNuevaEntidad(current));

        assertThat(saved.getVersion()).isZero();
        PasswordHistorial history = new PasswordHistorial(List.of(
                new PasswordHistorial.Entrada(
                        UUID.randomUUID(),
                        new HashContrasena("$2b$12$" + "x".repeat(53), AlgoritmoHash.BCRYPT),
                        currentAt.minusSeconds(86_400)),
                new PasswordHistorial.Entrada(UUID.randomUUID(), current.hashActual(), currentAt)));
        historiales.saveAll(PasswordHistorialPersistenceMapper.aEntidades(usuarioId, history));
        historiales.flush();

        Credencial loaded = CredencialPersistenceMapper.aDominio(
                credenciales.findById(usuarioId).orElseThrow());
        PasswordHistorial loadedHistory = PasswordHistorialPersistenceMapper.aDominio(
                historiales.findTop5ByUsuarioIdOrderByCreadoEnDescIdDesc(usuarioId));

        assertThat(loaded.version()).isZero();
        assertThat(loaded.hashActual().valor()).isEqualTo(currentHash);
        assertThat(loaded.actualizadoEn()).isEqualTo(currentAt);
        assertThat(loadedHistory.entradas()).hasSize(2);
        assertThat(loadedHistory.entradas().getLast().hash().valor()).isEqualTo(currentHash);
        assertThat(loaded.toString()).doesNotContain(currentHash);
    }

    @Test
    void rejectsLostUpdateWhenTwoWritersUseTheSameCredentialRevision() {
        guardarCredencialInicial();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        CredencialJpaEntity firstSnapshot = transaction.execute(status ->
                credenciales.findById(usuarioId).orElseThrow());
        CredencialJpaEntity staleSnapshot = transaction.execute(status ->
                credenciales.findById(usuarioId).orElseThrow());

        transaction.executeWithoutResult(status -> {
            firstSnapshot.reemplazarHash("$2b$12$" + "a".repeat(53),
                    Instant.parse("2026-10-14T17:00:00Z").atOffset(java.time.ZoneOffset.UTC));
            credenciales.saveAndFlush(firstSnapshot);
        });

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            staleSnapshot.reemplazarHash("$2b$12$" + "b".repeat(53),
                    Instant.parse("2026-10-14T17:01:00Z").atOffset(java.time.ZoneOffset.UTC));
            credenciales.saveAndFlush(staleSnapshot);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        CredencialJpaEntity persisted = transaction.execute(status ->
                credenciales.findById(usuarioId).orElseThrow());
        assertThat(persisted.getVersion()).isEqualTo(1L);
        assertThat(persisted.getPasswordHash()).startsWith("$2b$12$");
    }

    @Test
    void repositoryCanLockCredentialByOwnerInsideTransaction() {
        guardarCredencialInicial();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CredencialJpaEntity locked = transaction.execute(status ->
                credenciales.bloquearPorUsuarioId(usuarioId).orElseThrow());

        assertThat(locked.getId()).isEqualTo(usuarioId);
    }

    private void guardarCredencialInicial() {
        Credencial credential = new Credencial(
                usuarioId,
                usuarioId,
                new HashContrasena("$2b$12$" + "0".repeat(53), AlgoritmoHash.BCRYPT),
                Instant.parse("2026-10-14T16:00:00Z"),
                0);
        credenciales.saveAndFlush(CredencialPersistenceMapper.aNuevaEntidad(credential));
    }
}
