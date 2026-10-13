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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MfaJpaAdapter.class)
@Testcontainers
class MfaJpaPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6");
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final String HASH = "a".repeat(64);

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("mfa/base-v2-v3-fixture.sql"));
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
        jdbc.update("insert into usuario(id) values (?)", usuario);
        jdbc.update("insert into credencial(id,usuario_id) values (?,?)", credencial, usuario);
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
        jdbc.update("insert into usuario(id) values (?)", otro);
        var ajeno = Otp.nuevo(new ContextoOtp(otro, desafio, UUID.randomUUID()), HASH,
                "otp-test", CanalOtp.EMAIL, "otro@ejemplo.test", AHORA);
        assertThatThrownBy(() -> persistencia.guardarOtp(ajeno)).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("fk_otp_desafio_usuario");
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
        assertThatThrownBy(() -> jdbc.update("insert into otp(id,usuario_id,hash_codigo,destino,canal,motivo,creado_en,expira_en) values (?,?,?,?,?,?,?,?)",
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
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void guardadoYLockExigenTransaccionDelCoordinador() {
        var modelo = DesafioMfa.nuevo(desafio, usuario, credencial, 1, HASH, AHORA);
        assertThatThrownBy(() -> persistencia.guardarDesafio(modelo)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> persistencia.bloquearDesafio(desafio)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
