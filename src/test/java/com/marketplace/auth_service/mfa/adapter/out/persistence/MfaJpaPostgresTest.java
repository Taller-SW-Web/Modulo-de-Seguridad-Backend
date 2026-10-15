package com.marketplace.auth_service.mfa.adapter.out.persistence;

import com.marketplace.auth_service.mfa.domain.model.CanalOtp;
import com.marketplace.auth_service.mfa.domain.model.ContextoOtp;
import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import com.marketplace.auth_service.mfa.domain.model.Otp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MfaJpaAdapter.class, MfaJpaPostgresTest.PersistenciaPropia.class})
@Testcontainers
class MfaJpaPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.mfa.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.mfa.adapter.out.persistence.repository")
    static class PersistenciaPropia { }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final String HASH = "a".repeat(64);

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            // Solo esquema desechable: tablas MFA contrastadas con metadatos reales.
            try (var entrada = new ClassPathResource("mfa/base-observada/schema.sql").getInputStream();
                    var sentencia = conexion.createStatement()) {
                sentencia.execute(new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
            }
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("mfa/historicos-previos-v103.sql"));
            ScriptUtils.executeSqlScript(conexion, new FileSystemResource("docs/migraciones-propuestas/V103__mfa_desafio_otp.sql"));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar el PostgreSQL de prueba MFA", e);
        }
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MfaJpaAdapter persistencia;
    @Autowired JdbcTemplate jdbc;
    UUID usuario, credencial, desafio;

    @BeforeEach
    void fixture() {
        usuario = UUID.randomUUID();
        credencial = UUID.randomUUID();
        desafio = UUID.randomUUID();
        insertarUsuarioDePrueba(usuario);
        jdbc.update("insert into credencial(usuario_id,hash_contrasena) values (?,?)",
                usuario, "hash-de-fixture-no-utilizable");
    }

    private void insertarUsuarioDePrueba(UUID id) {
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado) values (?,?,?,?, 'ACTIVO')",
                id, id + "@ejemplo.test", "Cliente", "Prueba");
    }

    private DesafioMfa guardarDesafio() {
        var modelo = DesafioMfa.nuevo(desafio, usuario, credencial, 1, HASH, AHORA);
        persistencia.guardarDesafio(modelo);
        return modelo;
    }

    private Otp codigo(Instant creado, boolean usado, Instant invalidado) {
        return new Otp(new ContextoOtp(usuario, desafio, UUID.randomUUID()), HASH, "otp-test", CanalOtp.EMAIL,
                "cliente@ejemplo.test", creado, creado.plusSeconds(300), usado, 0, invalidado);
    }

    @Test
    void persisteYBloqueaDesafioConSuRevision() {
        var modelo = guardarDesafio();
        assertThat(persistencia.buscarDesafioPorHash(HASH)).contains(modelo);
        assertThat(persistencia.bloquearDesafio(desafio)).contains(modelo);
        assertThat(persistencia.buscarDesafioPorHash("b".repeat(64))).isEmpty();
    }

    @Test
    void persisteOtpYLoRecuperaSinEntidadesAjenas() {
        guardarDesafio();
        var codigo = codigo(AHORA, false, null);
        persistencia.guardarOtp(codigo);
        assertThat(persistencia.bloquearOtp(codigo.contexto().otpId())).contains(codigo);
        assertThat(persistencia.listarCodigosDelDesafio(desafio)).containsExactly(codigo);
        assertThat(persistencia.bloquearOtp(UUID.randomUUID())).isEmpty();
    }

    @Test
    void cuotaCuentaUsadosInvalidosYOtrosDesafiosConLimiteExclusivo() {
        guardarDesafio();
        Instant limite = AHORA.minusSeconds(900);
        persistencia.guardarOtp(codigo(limite, false, null));
        var usado = codigo(limite.plusSeconds(1), true, null);
        persistencia.guardarOtp(usado);
        var invalidado = codigo(AHORA.minusSeconds(2), false, AHORA.minusSeconds(1));
        persistencia.guardarOtp(invalidado);
        UUID otro = UUID.randomUUID();
        persistencia.guardarDesafio(DesafioMfa.nuevo(otro, usuario, credencial, 1, "b".repeat(64), AHORA));
        persistencia.guardarOtp(Otp.nuevo(new ContextoOtp(usuario, otro, UUID.randomUUID()), HASH,
                "otp-test", CanalOtp.EMAIL, "cliente@ejemplo.test", AHORA));
        assertThat(persistencia.contarSolicitudes(usuario, limite)).isEqualTo(3);
        assertThat(persistencia.primeraSolicitudEnVentana(usuario, limite)).contains(usado.creadoEn());
    }

    @Test
    void fkCompuestaRechazaOtpDeOtroUsuario() {
        guardarDesafio();
        UUID otro = UUID.randomUUID();
        insertarUsuarioDePrueba(otro);
        var ajeno = Otp.nuevo(new ContextoOtp(otro, desafio, UUID.randomUUID()), HASH,
                "otp-test", CanalOtp.EMAIL, "otro@ejemplo.test", AHORA);
        assertThatThrownBy(() -> persistencia.guardarOtp(ajeno)).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("otp_desafio_id_usuario_id_fkey");
    }

    @Test
    void nuevaFilaSinRevisionEsRechazadaPorPostgresql() {
        assertThatThrownBy(() -> jdbc.update("insert into desafio_mfa(id,usuario_id,hash_token,creado_en,expiracion) values (?,?,?,?,?)",
                desafio, usuario, HASH, OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(AHORA.plusSeconds(300), ZoneOffset.UTC)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nuevoOtpLoginSinContextoNoPasaConstraint() {
        assertThatThrownBy(() -> jdbc.update("insert into otp(id,usuario_id,hash_codigo,destino,canal,motivo,creado_en,expiracion) values (?,?,?,?,CAST(? AS canal_otp),?,?,?)",
                UUID.randomUUID(), usuario, HASH, "cliente@ejemplo.test", "EMAIL", "LOGIN_MFA",
                OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC), OffsetDateTime.ofInstant(AHORA.plusSeconds(300), ZoneOffset.UTC)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void historicosSeConservanPeroNoPuedenAutorizarLogin() {
        var historico = persistencia.buscarDesafioPorHash("f".repeat(64)).orElseThrow();
        assertThat(historico.credencialId()).isNull();
        assertThat(historico.vigenteEn(Instant.parse("2020-01-01T00:01:00Z"))).isFalse();
        UUID otpHistorico = UUID.fromString("00000000-0000-0000-0000-000000000093");
        assertThat(jdbc.queryForObject("select count(*) from otp where id=?", Long.class, otpHistorico)).isEqualTo(1);
        assertThat(persistencia.bloquearOtp(otpHistorico)).isEmpty();
        assertThat(jdbc.queryForObject("select motivo from otp where id=?", String.class, otpHistorico)).isNull();
        assertThat(jdbc.queryForObject("select destino from otp where id=?", String.class, otpHistorico)).isNull();
        assertThat(jdbc.queryForObject("select hash_key_id from otp where id=?", String.class, otpHistorico)).isNull();
        assertThat(jdbc.queryForObject("select usado from otp where id=?", Boolean.class, otpHistorico)).isFalse();
    }

    @Test
    void baseCompletaConservaTablasVistaYConstraintsDeOtrosDuenos() {
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema='public' and table_type='BASE TABLE'", Integer.class)).isEqualTo(22);
        assertThat(jdbc.queryForObject("select estado::text from usuario_estado_efectivo where usuario_id=?",
                String.class, usuario)).isEqualTo("ACTIVO");
        assertThat(jdbc.queryForObject("select count(*) from pg_trigger where tgname='tg_auditoria_solo_anexado'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_name='flyway_schema_history'", Integer.class)).isZero();
    }

    @Test
    void usuarioSinCredencialNoPuedeEmitirDesafio() {
        UUID sinCredencial = UUID.randomUUID();
        insertarUsuarioDePrueba(sinCredencial);
        var modelo = DesafioMfa.nuevo(desafio, sinCredencial, UUID.randomUUID(), 1, HASH, AHORA);
        assertThatThrownBy(() -> persistencia.guardarDesafio(modelo))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("fk_desafio_mfa_credencial_usuario");
    }

    @Test
    void revisionOpacaNoPresuponeNiCambiaLaPkDeCredenciales() {
        var modelo = guardarDesafio();
        assertThat(credencial).isNotEqualTo(usuario);
        assertThat(persistencia.buscarDesafioPorHash(HASH)).contains(modelo);
        assertThat(jdbc.queryForList("select column_name from information_schema.columns "
                + "where table_schema='public' and table_name='credencial' order by ordinal_position", String.class))
                .containsExactly("usuario_id", "hash_contrasena", "ultimo_cambio", "requiere_cambio");
    }

    @Test
    void reutilizaEnumFechaFkEIndiceDeLaBaseManual() {
        assertThat(jdbc.queryForObject("select udt_name from information_schema.columns "
                + "where table_schema='public' and table_name='otp' and column_name='canal'", String.class))
                .isEqualTo("canal_otp");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where table_schema='public' and table_name='otp' and column_name='expira_en'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint "
                + "where conrelid='otp'::regclass and confrelid='desafio_mfa'::regclass", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes "
                + "where schemaname='public' and tablename='otp' and indexname='idx_otp_usuario_fecha'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void unOtpNuevoNoPuedeHeredarPropositoNuloDeLosHistoricos() {
        assertThatThrownBy(() -> jdbc.update("insert into otp(id,usuario_id,hash_codigo,canal,creado_en,expiracion) "
                        + "values (?,?,?,'EMAIL',?,?)", UUID.randomUUID(), usuario, HASH,
                OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC), OffsetDateTime.ofInstant(AHORA.plusSeconds(300), ZoneOffset.UTC)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("ck_otp_proposito");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void guardadoYLockExigenTransaccionDelCoordinador() {
        var modelo = DesafioMfa.nuevo(desafio, usuario, credencial, 1, HASH, AHORA);
        assertThatThrownBy(() -> persistencia.guardarDesafio(modelo)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> persistencia.bloquearDesafio(desafio)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
