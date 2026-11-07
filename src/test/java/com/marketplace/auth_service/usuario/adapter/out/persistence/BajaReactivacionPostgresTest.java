package com.marketplace.auth_service.usuario.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.auditoria.adapter.in.transaction.AuditoriaTransaccional;
import com.marketplace.auth_service.auditoria.adapter.out.correo.EnvioCorreoSmtp;
import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesAesGcm;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.AuditoriaJpaAdapter;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.OutboxJpaAdapter;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.repository.OutboxJpaRepository;
import com.marketplace.auth_service.auditoria.adapter.out.transaccion.TareasTrasCommitSpring;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.out.AuditoriaPersistenciaPort;
import com.marketplace.auth_service.auditoria.application.port.out.CifradoMensajesPort;
import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import com.marketplace.auth_service.auditoria.application.service.AuditoriaService;
import com.marketplace.auth_service.auditoria.application.service.MensajeriaService;
import com.marketplace.auth_service.configuration.*;
import com.marketplace.auth_service.credencial.adapter.out.persistence.CredencialesJpaAdapter;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.mfa.adapter.out.persistence.MfaJpaAdapter;
import com.marketplace.auth_service.proteccion.adapter.out.persistence.ProteccionJpaAdapter;
import com.marketplace.auth_service.sesion.adapter.out.persistence.SesionJpaAdapter;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.port.in.LoginPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAdministracionCuenta;
import com.marketplace.auth_service.usuario.application.port.in.BajaReactivacionPort;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * FEAT-02 (SPEC-04) con los proveedores reales de cuentas, login, sesiones, autorización, auditoría y
 * outbox, sobre el esquema observado y las migraciones (misma composición que el bloqueo manual).
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BajaReactivacionPostgresTest.Config.class, UsuarioConfiguration.class, AutorizacionConfig.class,
        CredencialesConfig.class, CredencialesJpaAdapter.class, TokensUnUsoConfig.class,
        ProteccionConfig.class, ProteccionCriptografiaConfig.class, ProteccionJpaAdapter.class, EfectosConfig.class,
        SesionConfig.class, SesionJpaAdapter.class, IdentidadConfig.class, LoginConfig.class,
        MfaConfig.class, MfaCriptografiaConfig.class, MfaJpaAdapter.class, AuditoriaJpaAdapter.class,
        BajaReactivacionConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class BajaReactivacionPostgresTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service")
    @EnableJpaRepositories("com.marketplace.auth_service")
    static class Config {
        @Bean Clock reloj() { return Clock.systemUTC(); }
        @Bean CifradoMensajesPort cifrado() { return new CifradoMensajesAesGcm("test-feat02", new byte[32]); }
        @Bean OutboxPort outbox(OutboxJpaRepository filas) { return new OutboxJpaAdapter(filas); }
        @Bean AuditoriaPort auditoria(AuditoriaPersistenciaPort filas, PlatformTransactionManager tx) {
            return new AuditoriaTransaccional(new AuditoriaService(filas, new TareasTrasCommitSpring(tx)));
        }
        @Bean MensajeriaService mensajeria(OutboxPort outbox, CifradoMensajesPort cifrado, Clock reloj) {
            return new MensajeriaService(outbox, cifrado, new EnvioCorreoSmtp(null, "test@example.test"), reloj);
        }
    }

    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17.6");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) throws Exception {
        PG.start();
        try (var c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             var s = c.createStatement()) {
            try (var in = new ClassPathResource("sesion/base-observada/schema.sql").getInputStream()) {
                s.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            c.setAutoCommit(false);
            try (var migraciones = Files.list(Path.of("src/main/resources/db/migration"))) {
                for (var p : migraciones.filter(p -> p.toString().endsWith(".sql")).sorted().toList())
                    s.execute(Files.readString(p));
            }
            c.commit();
            s.execute("insert into rol(codigo,nombre) values('CLIENTE','Cliente'),('ADMIN_SISTEMA','Administrador')");
            s.execute("insert into permiso(codigo,modulo) values('usuario.desactivar','seguridad'),"
                    + "('usuario.reactivar','seguridad') on conflict do nothing");
            s.execute("insert into rol_permiso(rol_codigo,permiso_codigo) values('ADMIN_SISTEMA','usuario.desactivar'),"
                    + "('ADMIN_SISTEMA','usuario.reactivar') on conflict do nothing");
            c.commit();
        }
        Path comunes = Path.of("build/test-fixtures/feat02-comunes.txt").toAbsolutePath();
        Files.createDirectories(comunes.getParent());
        Files.writeString(comunes, "ComunDePrueba8!\n");
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("g7.password.comunes.archivo", comunes::toString);
        r.add("AUTH_FALLOS_HMAC_KEY_ID", () -> "test-feat02");
        r.add("AUTH_FALLOS_HMAC_KEY", () -> Base64.getEncoder().encodeToString(new byte[32]));
        r.add("AUTH_OTP_HMAC_KEY_ID", () -> "test-feat02");
        r.add("AUTH_OTP_HMAC_KEY", () -> Base64.getEncoder().encodeToString(new byte[32]));
        r.add("identidad.issuer", () -> "https://feat02.example.test/auth");
        r.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }

    private static final String CLAVE = "Clave-Segura-2026!";

    @Autowired BajaReactivacionPort operaciones;
    @Autowired LoginPort login;
    @Autowired SesionesPort sesiones;
    @Autowired PasswordHasherPort hasher;
    @Autowired MensajeriaService mensajeria;
    @Autowired JdbcTemplate jdbc;

    UUID cliente;
    String correo;

    ContextoPeticion contexto() { return new ContextoPeticion("127.0.0.1", "feat02", UUID.randomUUID()); }

    @BeforeEach
    void preparar() {
        cliente = UUID.randomUUID();
        correo = cliente + "@example.test";
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado,correo_verificado) values(?,?,'Cuenta','Baja','ACTIVO',true)",
                cliente, correo);
        jdbc.update("insert into usuario_rol(usuario_id,rol_codigo) values(?,'CLIENTE')", cliente);
        jdbc.update("insert into credencial(usuario_id,hash_contrasena,ultimo_cambio,version) values(?,?,now(),0)",
                cliente, hasher.generar(CLAVE).valor());
    }

    @AfterEach
    void retirarFallos() {
        jdbc.execute("alter table auditoria_seguridad drop constraint if exists feat02_fallo");
        jdbc.execute("alter table outbox drop constraint if exists feat02_fallo");
    }

    ActorUsuario administrador() {
        UUID admin = UUID.randomUUID();
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado,correo_verificado) values(?,?,'Admin','Prueba','ACTIVO',true)",
                admin, admin + "@example.test");
        jdbc.update("insert into usuario_rol(usuario_id,rol_codigo) values(?,'ADMIN_SISTEMA')", admin);
        return new ActorUsuario(admin, UUID.randomUUID(), Set.of("ADMIN_SISTEMA"), Set.of("usuario.desactivar", "usuario.reactivar"));
    }

    String estado(UUID id) { return jdbc.queryForObject("select estado::text from usuario where id=?", String.class, id); }
    long auditorias(String accion) {
        return jdbc.queryForObject("select count(*) from auditoria_seguridad where objetivo_usuario_id=? and accion=?",
                Long.class, cliente, accion);
    }
    long eventos(String topico) {
        return jdbc.queryForObject("select count(*) from outbox where topico=? and payload->>'usuarioId'=?",
                Long.class, topico, cliente.toString());
    }

    @Test
    void bajaDejaInactivaRevocaSesionesAuditaPublicaYElLoginRespondeComoCredencialInvalida() {
        var actor = administrador();
        var sesion = ((ResultadoLogin.Iniciada) login.iniciar(correo, CLAVE, contexto())).sesion();

        assertThat(operaciones.darDeBaja(actor, cliente.toString(), contexto())).isEqualTo(ResultadoAdministracionCuenta.HECHO);

        assertThat(estado(cliente)).isEqualTo("INACTIVO");
        assertThat(jdbc.queryForObject("select count(*) from usuario where id=?", Long.class, cliente)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select bool_and(revocada) from sesion_familia where usuario_id=?", Boolean.class, cliente)).isTrue();
        assertThat(sesiones.renovar(new SecretoRefresco(sesion.refreshToken()))).isInstanceOf(RenovacionResultado.Rechazada.class);
        assertThat(auditorias("USUARIO_DESACTIVADO")).isEqualTo(1);
        assertThat(eventos("usuario.desactivado")).isEqualTo(1);
        assertThat(login.iniciar(correo, CLAVE, contexto())).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);

        // Repetir la baja no duplica efectos.
        assertThat(operaciones.darDeBaja(actor, cliente.toString(), contexto())).isEqualTo(ResultadoAdministracionCuenta.HECHO);
        assertThat(auditorias("USUARIO_DESACTIVADO")).isEqualTo(1);
    }

    @Test
    void reactivarVuelveAActivoConLosMismosRolesSinSesionesYPublicaReactivadoNoCreado() {
        var actor = administrador();
        operaciones.darDeBaja(actor, cliente.toString(), contexto());

        assertThat(operaciones.reactivar(actor, cliente.toString(), contexto())).isEqualTo(ResultadoAdministracionCuenta.HECHO);

        assertThat(estado(cliente)).isEqualTo("ACTIVO");
        assertThat(jdbc.queryForList("select rol_codigo::text from usuario_rol where usuario_id=?", String.class, cliente))
                .containsExactly("CLIENTE");
        assertThat(jdbc.queryForObject("select count(*) from sesion_familia where usuario_id=? and not revocada",
                Long.class, cliente)).isZero();
        assertThat(auditorias("USUARIO_REACTIVADO")).isEqualTo(1);
        assertThat(eventos("usuario.reactivado")).isEqualTo(1);
        assertThat(eventos("usuario.creado")).isZero();
        assertThat(login.iniciar(correo, CLAVE, contexto())).isInstanceOf(ResultadoLogin.Iniciada.class);

        // RF-04.5: reactivar una cuenta ACTIVO no cambia nada ni publica (antes de procesar el outbox).
        assertThat(operaciones.reactivar(actor, cliente.toString(), contexto())).isEqualTo(ResultadoAdministracionCuenta.HECHO);
        assertThat(eventos("usuario.reactivado")).isEqualTo(1);

        // Sobres publicados: datos del catálogo para los dos eventos.
        List<Map<String, Object>> publicados = new ArrayList<>();
        mensajeria.procesarEventos((tipo, sobre) -> {
            if (cliente.toString().equals(sobre.get("usuarioId"))) publicados.add(sobre);
        });
        assertThat(publicados).extracting(s -> s.get("tipo")).containsExactlyInAnyOrder("usuario.desactivado", "usuario.reactivado");
        assertThat(publicados).filteredOn(s -> "usuario.desactivado".equals(s.get("tipo"))).singleElement()
                .satisfies(s -> assertThat(s.get("datos")).isEqualTo(Map.of("motivo", "BAJA_ADMINISTRATIVA")));
        assertThat(publicados).filteredOn(s -> "usuario.reactivado".equals(s.get("tipo"))).singleElement()
                .satisfies(s -> assertThat(s.get("datos")).isEqualTo(Map.of("roles", List.of("CLIENTE"))));
    }

    @Test
    void siFallaLaAuditoriaCriticaLaBajaSeRevierteConSusSesiones() {
        var actor = administrador();
        login.iniciar(correo, CLAVE, contexto());
        jdbc.execute("alter table auditoria_seguridad add constraint feat02_fallo check (accion <> 'USUARIO_DESACTIVADO') not valid");

        assertThatThrownBy(() -> operaciones.darDeBaja(actor, cliente.toString(), contexto())).isInstanceOf(RuntimeException.class);

        assertThat(estado(cliente)).isEqualTo("ACTIVO");
        assertThat(jdbc.queryForObject("select bool_or(revocada) from sesion_familia where usuario_id=?", Boolean.class, cliente)).isFalse();
        assertThat(eventos("usuario.desactivado")).isZero();
    }

    @Test
    void nadieSeDaDeBajaASiMismoYSinPermisoNoCambiaNada() {
        var actor = administrador();
        assertThat(operaciones.darDeBaja(actor, actor.sub().toString(), contexto()))
                .isEqualTo(ResultadoAdministracionCuenta.ADMINISTRADOR_PROTEGIDO);
        assertThat(estado(actor.sub())).isEqualTo("ACTIVO");

        var comun = new ActorUsuario(cliente, UUID.randomUUID(), Set.of("CLIENTE"), Set.of());
        assertThat(operaciones.darDeBaja(comun, actor.sub().toString(), contexto()))
                .isEqualTo(ResultadoAdministracionCuenta.SIN_PERMISO);
        assertThat(estado(actor.sub())).isEqualTo("ACTIVO");
    }

    @Test
    void reactivarUnaCuentaQueSeDioDeBajaSinVerificarLaDejaPendiente() {
        var actor = administrador();
        jdbc.update("update usuario set estado='PENDIENTE_VERIFICACION'::estado_cuenta, correo_verificado=false where id=?", cliente);
        operaciones.darDeBaja(actor, cliente.toString(), contexto());

        operaciones.reactivar(actor, cliente.toString(), contexto());

        assertThat(estado(cliente)).isEqualTo("PENDIENTE_VERIFICACION");
    }
}
