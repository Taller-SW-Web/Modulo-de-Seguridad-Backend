package com.marketplace.auth_service.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesAesGcm;
import com.marketplace.auth_service.auditoria.adapter.out.correo.EnvioCorreoSmtp;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.OutboxJpaAdapter;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.repository.OutboxJpaRepository;
import com.marketplace.auth_service.auditoria.application.port.out.CifradoMensajesPort;
import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import com.marketplace.auth_service.auditoria.application.service.MensajeriaService;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import com.marketplace.auth_service.credencial.adapter.out.persistence.CredencialesJpaAdapter;
import com.marketplace.auth_service.perfil.SmtpLocalDePrueba;
import com.marketplace.auth_service.perfil.application.dto.ResultadoSolicitudCorreo;
import com.marketplace.auth_service.perfil.application.dto.SolicitudCambioCorreo;
import com.marketplace.auth_service.perfil.application.port.in.CambioCorreoPort;
import com.marketplace.auth_service.perfil.domain.exception.CambioCorreoNoPermitidaException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * FEAT-04-T3 con PostgreSQL real y un receptor SMTP local (evidencia que pide la tarea). Compone los
 * proveedores reales: cuentas, credenciales (Argon2id), tokens de un uso y outbox cifrado; el correo
 * sale por el {@code JavaMailSender} real hacia {@link SmtpLocalDePrueba}. Comprueba que el correo
 * del usuario no cambia al solicitar, que el token queda ligado a la dirección nueva (solo su hash en
 * la tabla), que el outbox no guarda direcciones ni enlaces en claro y que lo recibido por SMTP es
 * el enlace al nuevo correo y el aviso enmascarado al anterior.
 *
 * <p>Usa {@code PRUEBAS_POSTGRES_URL} o Docker; sin ninguno se omite (nunca Supabase).
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC", "g7.fixtures.cuentas.habilitadas=true",
        "g7.spa.url=https://spa.g7.test"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({UsuarioConfiguration.class, AutorizacionConfig.class, CredencialesConfig.class, CredencialesJpaAdapter.class,
        CuentasPruebaConfig.class, TokensUnUsoConfig.class, CambioCorreoConfig.class,
        CambioCorreoPostgresSmtpTest.Mensajeria.class, CambioCorreoPostgresSmtpTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CambioCorreoPostgresSmtpTest {

    private static final String CLAVE = "Zorzal-Andino-2026!";
    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000f00a");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-00000000f00b");
    private static final String CORREO_A = "cliente.a@fixtures.g7.test";
    private static final String CORREO_B = "cliente.b@fixtures.g7.test";
    private static final String NUEVO = "nuevo.a@ejemplo.test";
    private static final Pattern ENLACE = Pattern.compile("https://spa\\.g7\\.test/verificar-correo\\?token=([^\\s]+)");

    private static final SmtpLocalDePrueba SMTP = SmtpLocalDePrueba.iniciar();
    private static PostgreSQLContainer<?> contenedor;

    /** Entidades y repositorios de los dueños que compone la prueba (cada tabla, su dueño). */
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan({"com.marketplace.auth_service.usuario.adapter.out.persistence.entity",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.credencial.adapter.out.persistence.entity",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.entity"})
    @EnableJpaRepositories({"com.marketplace.auth_service.usuario.adapter.out.persistence.repository",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.credencial.adapter.out.persistence.repository",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.repository"})
    static class Persistencia { }

    /** Outbox y mensajería reales (cifrado AES-GCM) con el SMTP real apuntando al receptor local. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Mensajeria {
        @Bean
        CifradoMensajesPort cifradoMensajesPort() {
            return new CifradoMensajesAesGcm("prueba-t3", "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        }

        @Bean
        OutboxPort outboxPort(OutboxJpaRepository filas) {
            return new OutboxJpaAdapter(filas);
        }

        @Bean
        JavaMailSender javaMailSender() {
            var correo = new JavaMailSenderImpl();
            correo.setHost("127.0.0.1");
            correo.setPort(SMTP.puerto());
            Properties p = correo.getJavaMailProperties();
            p.put("mail.smtp.connectiontimeout", "5000");
            p.put("mail.smtp.timeout", "5000");
            return correo;
        }

        @Bean
        MensajeriaService mensajeriaService(OutboxPort outbox, CifradoMensajesPort cifrado, JavaMailSender correo) {
            return new MensajeriaService(outbox, cifrado, new EnvioCorreoSmtp(correo, "no-responder@g7.test"),
                    Clock.systemUTC());
        }
    }

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
        String nombre = "cambio_correo_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        try (var admin = DriverManager.getConnection(url, usuario, contrasena); var s = admin.createStatement()) {
            s.execute("CREATE DATABASE " + nombre);
        }
        String propia = url.replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
        try (var c = DriverManager.getConnection(propia, usuario, contrasena); var s = c.createStatement()) {
            s.execute(leer(new ClassPathResource("mfa/base-observada/schema.sql")));
            ScriptUtils.executeSqlScript(c, new FileSystemResource("docs/migraciones-propuestas/V101__credencial_version.sql"));
            ScriptUtils.executeSqlScript(c,
                    new FileSystemResource("docs/migraciones-propuestas/V105__token_un_uso_contexto_id.sql"));
            s.execute(Files.readString(Path.of(
                    "docs/migraciones-propuestas/V106__auditoria_registro_id_y_outbox_reintento.sql")));
            s.execute(leer(new ClassPathResource("db/autorizacion/R__catalogo_autorizacion.sql")));
        }
        Path comunes = Files.createTempFile("feat04-t3-comunes-", ".txt");
        Files.writeString(comunes, "ComunDePrueba8!\n");
        registro.add("spring.datasource.url", () -> propia);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
        registro.add("g7.fixtures.cuentas.clave", () -> CLAVE);
        registro.add("g7.password.comunes.archivo", comunes::toString);
    }

    private static String leer(ClassPathResource recurso) throws Exception {
        try (var in = recurso.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @AfterAll
    static void cerrarSmtp() {
        SMTP.close();
    }

    @Autowired CambioCorreoPort cambio;
    @Autowired MensajeriaService mensajeria;
    @Autowired CargaCuentasPrueba carga;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepararCuentas() {
        jdbc.execute("truncate usuario cascade");
        jdbc.execute("truncate outbox");
        SMTP.limpiar();
        carga.cargar();
    }

    private ResultadoSolicitudCorreo solicitar(UUID actor, UUID cuenta, String nuevo, String clave) {
        return cambio.solicitar(new ActorUsuario(actor, UUID.randomUUID(), Set.of("CLIENTE"), Set.of()), cuenta,
                new SolicitudCambioCorreo(nuevo, clave),
                new ContextoPeticion("203.0.113.7", "JUnit", UUID.randomUUID()));
    }

    private int tokensCambioCorreo() {
        return jdbc.queryForObject("select count(*) from token_un_uso where proposito = 'CAMBIO_CORREO'::proposito_token",
                Integer.class);
    }

    private int filasOutbox() {
        return jdbc.queryForObject("select count(*) from outbox", Integer.class);
    }

    private static String texto(SmtpLocalDePrueba.Recibido r) throws Exception {
        var mensaje = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(r.datos()));
        return mensaje.getContent().toString();
    }

    private static SmtpLocalDePrueba.Recibido para(String destino) {
        return SMTP.recibidos().stream().filter(r -> r.destinatarios().contains(destino)).findFirst().orElseThrow();
    }

    private static String sha256Hex(String claro) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(claro.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void esc163ElCorreoNoCambiaAlSolicitarYElTokenQuedaLigadoALaDireccionNueva() throws Exception {
        assertThat(solicitar(A, A, "  Nuevo.A@Ejemplo.test ", CLAVE)).isEqualTo(ResultadoSolicitudCorreo.ENVIADA);

        // El correo del usuario sigue siendo el anterior y la dirección nueva no es de ninguna cuenta.
        assertThat(jdbc.queryForObject("select correo from usuario where id = ?", String.class, A)).isEqualTo(CORREO_A);
        assertThat(jdbc.queryForObject("select count(*) from usuario where correo = ?", Integer.class, NUEVO)).isZero();

        // Un token CAMBIO_CORREO vigente, de esta cuenta, ligado a la dirección nueva y sin valor claro.
        List<Map<String, Object>> tokens = jdbc.queryForList("select usuario_id, destino, usado, hash_token "
                + "from token_un_uso where proposito = 'CAMBIO_CORREO'::proposito_token");
        assertThat(tokens).singleElement().satisfies(fila -> {
            assertThat(fila.get("usuario_id")).isEqualTo(A);
            assertThat(fila.get("destino")).isEqualTo(NUEVO);
            assertThat(fila.get("usado")).isEqualTo(false);
        });

        // Dos correos al outbox, entregados por el SMTP real al receptor local.
        assertThat(filasOutbox()).isEqualTo(2);
        assertThat(mensajeria.procesarCorreos()).isEqualTo(2);
        assertThat(SMTP.recibidos()).hasSize(2);

        // Enlace a la dirección nueva: su token coincide con el hash guardado.
        var confirmacion = texto(para(NUEVO));
        var enlace = ENLACE.matcher(confirmacion);
        assertThat(enlace.find()).as("la confirmación trae el enlace a la SPA").isTrue();
        String tokenClaro = URLDecoder.decode(enlace.group(1), StandardCharsets.UTF_8);
        assertThat(tokens.get(0).get("hash_token")).isEqualTo(sha256Hex(tokenClaro)).isNotEqualTo(tokenClaro);
        assertThat(confirmacion).containsPattern("vence en \\d+ (horas?|minutos?)");

        // Aviso a la dirección anterior: enmascara la nueva, no lleva enlace.
        var aviso = texto(para(CORREO_A));
        assertThat(aviso).contains("n***@ejemplo.test").doesNotContain(NUEVO).doesNotContain("verificar-correo");
    }

    @Test
    void elOutboxNoGuardaDireccionesNiEnlacesEnClaro() {
        solicitar(A, A, NUEVO, CLAVE);

        List<String> payloads = jdbc.queryForList("select payload::text from outbox", String.class);
        assertThat(payloads).hasSize(2).allSatisfy(payload -> assertThat(payload)
                .doesNotContain(NUEVO).doesNotContain(CORREO_A).doesNotContain("verificar-correo")
                .doesNotContain("spa.g7.test"));
    }

    @Test
    void esc164ConLaContrasenaEquivocadaNoSeEmiteTokenNiSeEnviaNada() {
        assertThat(solicitar(A, A, NUEVO, CLAVE + "x")).isEqualTo(ResultadoSolicitudCorreo.CONTRASENA_INCORRECTA);

        assertThat(tokensCambioCorreo()).isZero();
        assertThat(filasOutbox()).isZero();
        assertThat(mensajeria.procesarCorreos()).isZero();
        assertThat(SMTP.recibidos()).isEmpty();
        assertThat(jdbc.queryForObject("select correo from usuario where id = ?", String.class, A)).isEqualTo(CORREO_A);
    }

    @Test
    void unCorreoYaRegistradoOElMismoActualNoEmitenTokenNiEnvianNada() {
        assertThat(solicitar(A, A, CORREO_B, CLAVE)).isEqualTo(ResultadoSolicitudCorreo.CORREO_NO_DISPONIBLE);
        assertThat(solicitar(A, A, "  " + CORREO_B.toUpperCase(Locale.ROOT) + " ", CLAVE))
                .isEqualTo(ResultadoSolicitudCorreo.CORREO_NO_DISPONIBLE);
        assertThat(solicitar(A, A, CORREO_A, CLAVE)).isEqualTo(ResultadoSolicitudCorreo.CORREO_NO_DISPONIBLE);

        assertThat(tokensCambioCorreo()).isZero();
        assertThat(filasOutbox()).isZero();
        assertThat(mensajeria.procesarCorreos()).isZero();
        assertThat(SMTP.recibidos()).isEmpty();
    }

    @Test
    void esc165OtraCuentaNoPuedeSolicitarElCambioDeLaAjena() {
        assertThatThrownBy(() -> solicitar(B, A, NUEVO, CLAVE)).isInstanceOf(CambioCorreoNoPermitidaException.class);

        assertThat(tokensCambioCorreo()).isZero();
        assertThat(filasOutbox()).isZero();
        assertThat(jdbc.queryForObject("select correo from usuario where id = ?", String.class, A)).isEqualTo(CORREO_A);
    }

    @Test
    void unaSegundaSolicitudAnulaElEnlaceAnterior() throws Exception {
        assertThat(solicitar(A, A, "primero.a@ejemplo.test", CLAVE)).isEqualTo(ResultadoSolicitudCorreo.ENVIADA);
        assertThat(solicitar(A, A, NUEVO, CLAVE)).isEqualTo(ResultadoSolicitudCorreo.ENVIADA);

        assertThat(jdbc.queryForObject("select count(*) from token_un_uso where proposito = "
                + "'CAMBIO_CORREO'::proposito_token and usado", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("select destino from token_un_uso where proposito = "
                + "'CAMBIO_CORREO'::proposito_token and not usado", String.class)).containsExactly(NUEVO);

        // El correo del primer intento llegó, pero su token ya no sirve; solo el último queda vigente.
        assertThat(mensajeria.procesarCorreos()).isEqualTo(4);
        var enlaceViejo = ENLACE.matcher(texto(para("primero.a@ejemplo.test")));
        assertThat(enlaceViejo.find()).isTrue();
        String hashViejo = sha256Hex(URLDecoder.decode(enlaceViejo.group(1), StandardCharsets.UTF_8));
        assertThat(jdbc.queryForObject("select usado from token_un_uso where hash_token = ?", Boolean.class, hashViejo))
                .isTrue();
    }
}
