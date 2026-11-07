package com.marketplace.auth_service.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesAesGcm;
import com.marketplace.auth_service.auditoria.adapter.out.correo.EnvioCorreoSmtp;
import com.marketplace.auth_service.auditoria.application.dto.PaginaActividad;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
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
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoVerificacion;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.port.in.VerificacionCorreoPort;
import com.marketplace.auth_service.usuario.application.port.out.CorreoVerificacionPort;
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
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * FEAT-04-T3, confirmación (SPEC-16, ESC-16.3) con PostgreSQL real y un receptor SMTP local: se solicita el
 * cambio, el enlace llega por SMTP, y {@code POST /auth/verificar-correo} (aquí, {@link VerificacionCorreoPort})
 * lo confirma. Comprueba que solo entonces cambia {@code usuario.correo} (ya verificado), que el token queda
 * usado, que se registra {@code CORREO_CAMBIADO} y se publica {@code usuario.atributos_actualizados} sin
 * direcciones, y que un enlace vencido, repetido, anulado por otra solicitud o con la dirección ya tomada no
 * cambia nada. La auditoría se registra en memoria y el correo de verificación de cuentas nuevas no se usa.
 *
 * <p>Usa {@code PRUEBAS_POSTGRES_URL} o Docker; sin ninguno se omite (nunca Supabase).
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC", "g7.fixtures.cuentas.habilitadas=true",
        "g7.spa.url=https://spa.g7.test"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({UsuarioConfiguration.class, AutorizacionConfig.class, CredencialesConfig.class, CredencialesJpaAdapter.class,
        CuentasPruebaConfig.class, TokensUnUsoConfig.class, CambioCorreoConfig.class, VerificacionCorreoConfig.class,
        CambioCorreoConfirmacionPostgresSmtpTest.Mensajeria.class, CambioCorreoConfirmacionPostgresSmtpTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CambioCorreoConfirmacionPostgresSmtpTest {

    private static final String CLAVE = "Zorzal-Andino-2026!";
    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000f00a");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-00000000f00b");
    private static final String CORREO_A = "cliente.a@fixtures.g7.test";
    private static final String CORREO_B = "cliente.b@fixtures.g7.test";
    private static final String NUEVO = "nuevo.a@ejemplo.test";
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.9", "JUnit", UUID.randomUUID());
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
        AuditoriaEnMemoria auditoriaEnMemoria() {
            return new AuditoriaEnMemoria();
        }

        /** La confirmación de un cambio de correo no envía correos de cuenta nueva; el reenvío no se ejerce aquí. */
        @Bean
        CorreoVerificacionPort correoVerificacionPort() {
            return (usuarioId, correo, nombres, canal) -> { };
        }

        @Bean
        MensajeriaService mensajeriaService(OutboxPort outbox, CifradoMensajesPort cifrado, JavaMailSender correo) {
            return new MensajeriaService(outbox, cifrado, new EnvioCorreoSmtp(correo, "no-responder@g7.test"),
                    Clock.systemUTC());
        }
    }

    static final class AuditoriaEnMemoria implements AuditoriaPort {
        final List<RegistroSeguro> registros = new CopyOnWriteArrayList<>();
        @Override public void registrarCritica(RegistroSeguro registro) { registros.add(registro); }
        @Override public void registrarNoCriticaTrasCommit(RegistroSeguro registro) { registros.add(registro); }
        @Override public PaginaActividad actividadPropia(UUID titular, int pagina, int tamano) {
            throw new UnsupportedOperationException();
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
    @Autowired VerificacionCorreoPort verificacion;
    @Autowired CuentasPort cuentas;
    @Autowired AuditoriaEnMemoria auditoria;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepararCuentas() {
        jdbc.execute("truncate usuario cascade");
        jdbc.execute("truncate outbox");
        SMTP.limpiar();
        auditoria.registros.clear();
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

    /** Token claro del enlace que llegó por SMTP a esa dirección, tras entregar los correos del outbox. */
    private String tokenDelEnlaceA(String destino) throws Exception {
        mensajeria.procesarCorreos();
        var enlace = ENLACE.matcher(texto(para(destino)));
        assertThat(enlace.find()).as("el correo a " + destino + " trae el enlace a la SPA").isTrue();
        return URLDecoder.decode(enlace.group(1), StandardCharsets.UTF_8);
    }

    private String correoDe(UUID id) {
        return jdbc.queryForObject("select correo from usuario where id = ?", String.class, id);
    }

    private int eventosAtributos() {
        return jdbc.queryForObject("select count(*) from outbox where topico = 'usuario.atributos_actualizados'",
                Integer.class);
    }

    private List<RegistroSeguro> correoCambiado() {
        return auditoria.registros.stream().filter(r -> "CORREO_CAMBIADO".equals(r.accion())).toList();
    }

    @Test
    void esc163AlConfirmarElEnlaceElCorreoCambiaYQuedaVerificado() throws Exception {
        assertThat(solicitar(A, A, NUEVO, CLAVE)).isEqualTo(ResultadoSolicitudCorreo.ENVIADA);
        String token = tokenDelEnlaceA(NUEVO);
        assertThat(correoDe(A)).isEqualTo(CORREO_A); // hasta confirmar sigue el anterior

        assertThat(verificacion.verificar(token, CONTEXTO)).isEqualTo(ResultadoVerificacion.VERIFICADA);

        assertThat(correoDe(A)).isEqualTo(NUEVO);
        assertThat(jdbc.queryForObject("select correo_verificado from usuario where id = ?", Boolean.class, A)).isTrue();
        assertThat(cuentas.buscarPorCorreoNormalizado(NUEVO)).isPresent();
        assertThat(cuentas.buscarPorCorreoNormalizado(NUEVO).orElseThrow().id()).isEqualTo(A);
        assertThat(cuentas.buscarPorCorreoNormalizado(CORREO_A)).isEmpty();
        assertThat(jdbc.queryForObject("select usado from token_un_uso where hash_token = ?", Boolean.class,
                sha256Hex(token))).isTrue();
    }

    @Test
    void laConfirmacionRegistraCorreoCambiadoYPublicaElEventoSinDirecciones() throws Exception {
        solicitar(A, A, NUEVO, CLAVE);
        String token = tokenDelEnlaceA(NUEVO);

        verificacion.verificar(token, CONTEXTO);

        assertThat(correoCambiado()).singleElement().satisfies(r -> {
            assertThat(r.resultado()).isEqualTo("EXITO");
            assertThat(r.objetivoUsuarioId()).isEqualTo(A);
            assertThat(r.detalle().toString()).doesNotContain(NUEVO).doesNotContain(CORREO_A);
        });
        assertThat(eventosAtributos()).isEqualTo(1);
        List<String> payloads = jdbc.queryForList(
                "select payload::text from outbox where topico = 'usuario.atributos_actualizados'", String.class);
        assertThat(payloads).singleElement().satisfies(p -> assertThat(p)
                .contains("correo").doesNotContain(NUEVO).doesNotContain(CORREO_A));
    }

    @Test
    void elEnlaceNoSirveDosVecesYLaSegundaConfirmacionNoCambiaNiRegistraNada() throws Exception {
        solicitar(A, A, NUEVO, CLAVE);
        String token = tokenDelEnlaceA(NUEVO);
        assertThat(verificacion.verificar(token, CONTEXTO)).isEqualTo(ResultadoVerificacion.VERIFICADA);

        assertThat(verificacion.verificar(token, CONTEXTO)).isEqualTo(ResultadoVerificacion.YA_USADA);

        assertThat(correoDe(A)).isEqualTo(NUEVO);
        assertThat(correoCambiado()).hasSize(1);
        assertThat(eventosAtributos()).isEqualTo(1);
    }

    @Test
    void unEnlaceVencidoNoCambiaElCorreoNiPublicaNada() throws Exception {
        solicitar(A, A, NUEVO, CLAVE);
        String token = tokenDelEnlaceA(NUEVO);
        jdbc.update("update token_un_uso set creado_en = now() - interval '48 hours', "
                + "expiracion = now() - interval '24 hours' where hash_token = ?", sha256Hex(token));

        assertThat(verificacion.verificar(token, CONTEXTO)).isEqualTo(ResultadoVerificacion.EXPIRADA);

        assertThat(correoDe(A)).isEqualTo(CORREO_A);
        assertThat(correoCambiado()).isEmpty();
        assertThat(eventosAtributos()).isZero();
    }

    @Test
    void siOtraCuentaTomoLaDireccionEntreLaSolicitudYLaConfirmacionNoCambiaNada() throws Exception {
        solicitar(A, A, NUEVO, CLAVE);
        String token = tokenDelEnlaceA(NUEVO);
        jdbc.update("update usuario set correo = ? where id = ?", NUEVO, B);

        assertThat(verificacion.verificar(token, CONTEXTO)).isEqualTo(ResultadoVerificacion.YA_USADA);

        assertThat(correoDe(A)).isEqualTo(CORREO_A);
        assertThat(correoDe(B)).isEqualTo(NUEVO);
        assertThat(jdbc.queryForObject("select usado from token_un_uso where hash_token = ?", Boolean.class,
                sha256Hex(token))).isFalse(); // la reversión no gasta el enlace
        assertThat(correoCambiado()).isEmpty();
        assertThat(eventosAtributos()).isZero();
    }

    @Test
    void unaSegundaSolicitudAnulaElEnlaceAnteriorYSoloElUltimoConfirma() throws Exception {
        solicitar(A, A, "primero.a@ejemplo.test", CLAVE);
        solicitar(A, A, NUEVO, CLAVE);
        mensajeria.procesarCorreos();
        var viejo = ENLACE.matcher(texto(para("primero.a@ejemplo.test")));
        assertThat(viejo.find()).isTrue();
        String tokenViejo = URLDecoder.decode(viejo.group(1), StandardCharsets.UTF_8);
        String tokenNuevo = tokenDelEnlaceA(NUEVO);

        assertThat(verificacion.verificar(tokenViejo, CONTEXTO)).isEqualTo(ResultadoVerificacion.YA_USADA);
        assertThat(correoDe(A)).isEqualTo(CORREO_A);

        assertThat(verificacion.verificar(tokenNuevo, CONTEXTO)).isEqualTo(ResultadoVerificacion.VERIFICADA);
        assertThat(correoDe(A)).isEqualTo(NUEVO);
    }

    @Test
    void laConfirmacionSoloTocaLaCuentaDelToken() throws Exception {
        solicitar(A, A, NUEVO, CLAVE);
        String token = tokenDelEnlaceA(NUEVO);

        verificacion.verificar(token, CONTEXTO);

        assertThat(correoDe(B)).isEqualTo(CORREO_B);
        assertThat(correoDe(A)).isEqualTo(NUEVO);
    }
}
