package com.marketplace.auth_service.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.auditoria.adapter.out.correo.EnvioCorreoSmtp;
import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesAesGcm;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.OutboxJpaAdapter;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.repository.OutboxJpaRepository;
import com.marketplace.auth_service.auditoria.application.dto.PaginaActividad;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.out.CifradoMensajesPort;
import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import com.marketplace.auth_service.auditoria.application.service.MensajeriaService;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.credencial.adapter.out.persistence.CredencialesJpaAdapter;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.service.TokenUnUsoHashing;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.dto.SolicitudRegistro;
import com.marketplace.auth_service.usuario.application.port.in.RegistroClientePort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * FEAT-01-T1/T2 con los proveedores reales de cuentas, credenciales (hasher y política),
 * autorización, tokens de un uso y outbox cifrado sobre el esquema observado; la auditoría
 * se registra en memoria. Cada clase crea su propia base desechable; nunca Supabase.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC", "g7.registro.version-terminos=2026-10",
        "g7.spa.url=https://spa.example.test/"},
        showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({UsuarioConfiguration.class, AutorizacionConfig.class, CredencialesConfig.class, CredencialesJpaAdapter.class,
        TokensUnUsoConfig.class, RegistroClienteConfig.class, RegistroClientePostgresTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RegistroClientePostgresTest {

    private static final String CLAVE = "Zorzal-Andino-2026!";
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.7", "pruebas", UUID.randomUUID());
    private static final Pattern ENLACE = Pattern.compile("https://spa\\.example\\.test/verificar-correo\\?token=(\\S+)");

    /** Entidades de los dueños que compone el registro, outbox real y una auditoría en memoria. */
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan({"com.marketplace.auth_service.usuario.adapter.out.persistence.entity",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.credencial.adapter.out.persistence.entity",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.entity"})
    @EnableJpaRepositories({"com.marketplace.auth_service.usuario.adapter.out.persistence.repository",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.credencial.adapter.out.persistence.repository",
            "com.marketplace.auth_service.auditoria.adapter.out.persistence.repository"})
    static class Persistencia {
        @Bean AuditoriaEnMemoria auditoria() { return new AuditoriaEnMemoria(); }
        @Bean CifradoMensajesPort cifrado() { return new CifradoMensajesAesGcm("test-feat01", new byte[32]); }
        @Bean OutboxPort outbox(OutboxJpaRepository filas) { return new OutboxJpaAdapter(filas); }
        @Bean MensajeriaService mensajeria(OutboxPort outbox, CifradoMensajesPort cifrado) {
            return new MensajeriaService(outbox, cifrado, new EnvioCorreoSmtp(null, "test@example.test"), Clock.systemUTC());
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
        String nombre = "registro_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        try (var admin = DriverManager.getConnection(url, usuario, contrasena); var s = admin.createStatement()) {
            s.execute("CREATE DATABASE " + nombre);
        }
        String propia = url.replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
        try (var c = DriverManager.getConnection(propia, usuario, contrasena); var s = c.createStatement()) {
            s.execute(leer("mfa/base-observada/schema.sql"));
            ScriptUtils.executeSqlScript(c, new FileSystemResource("docs/migraciones-propuestas/V101__credencial_version.sql"));
            ScriptUtils.executeSqlScript(c,
                    new FileSystemResource("docs/migraciones-propuestas/V105__token_un_uso_contexto_id.sql"));
            s.execute(leer("db/autorizacion/R__catalogo_autorizacion.sql"));
        }
        Path comunes = Files.createTempFile("feat01-t1-comunes-", ".txt");
        Files.writeString(comunes, "ComunDePrueba8!\n");
        registro.add("spring.datasource.url", () -> propia);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
        registro.add("g7.password.comunes.archivo", comunes::toString);
    }

    private static String leer(String recurso) throws Exception {
        try (var in = new ClassPathResource(recurso).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Autowired RegistroClientePort registro;
    @Autowired CredencialesPort credenciales;
    @Autowired AutorizacionPort autorizacion;
    @Autowired AuditoriaEnMemoria auditoria;
    @Autowired CifradoMensajesPort cifrado;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void vaciar() {
        jdbc.execute("truncate usuario cascade");
        jdbc.execute("truncate outbox");
        auditoria.registros.clear();
    }

    @AfterEach
    void retirarFallosInducidos() {
        jdbc.execute("alter table outbox drop constraint if exists feat01_fallo");
    }

    private static SolicitudRegistro solicitud(String correo, String clave) {
        return new SolicitudRegistro(correo, "María", "Quispe Rojas", "+51987654321", "MARKETPLACE", clave);
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla, Integer.class);
    }

    private List<Map<String, Object>> correosDeVerificacion() {
        return jdbc.queryForList("select estado, payload::text as carga from outbox "
                + "where topico = 'correo.verificacion_correo'");
    }

    private String descifrar(String carga, String campo) throws Exception {
        return cifrado.descifrar(new ObjectMapper().readTree(carga).path(campo).asText());
    }

    @Test
    void emiteUnTokenDeVerificacionYEncolaElCorreoCifradoConSuEnlace() throws Exception {
        var id = ((ResultadoRegistro.Creada) registro.registrar(solicitud("maria@ejemplo.com", CLAVE), CONTEXTO)).id();

        var token = jdbc.queryForMap("select hash_token, usado, expiracion > now() + interval '23 hours' as vigente "
                + "from token_un_uso where usuario_id = ? and proposito = 'VERIFICAR_CORREO'", id);
        assertThat(token).containsEntry("usado", false).containsEntry("vigente", true);
        var correos = correosDeVerificacion();
        assertThat(correos).singleElement().satisfies(f -> assertThat(f.get("estado")).isEqualTo("PENDIENTE"));
        String carga = (String) correos.getFirst().get("carga");
        assertThat(carga).doesNotContain("maria@ejemplo.com", "verificar-correo");
        assertThat(descifrar(carga, "destino")).isEqualTo("maria@ejemplo.com");

        var enlace = ENLACE.matcher(descifrar(carga, "contenido"));
        assertThat(enlace.find()).isTrue();
        String claro = URLDecoder.decode(enlace.group(1), StandardCharsets.UTF_8);
        assertThat(TokenUnUsoHashing.sha256Hex(claro)).isEqualTo(token.get("hash_token"));
        assertThat(token.get("hash_token")).isNotEqualTo(claro);
        assertThat(auditoria.registros).allSatisfy(r -> assertThat(r.detalle().values()).doesNotContain(claro));
    }

    @Test
    void colaDeCorreoCaidaRevierteLaCuentaSinTokenNiCorreo() {
        jdbc.execute("alter table outbox add constraint feat01_fallo check (topico <> 'correo.verificacion_correo') not valid");

        assertThatThrownBy(() -> registro.registrar(solicitud("maria@ejemplo.com", CLAVE), CONTEXTO))
                .isInstanceOf(VerificacionNoDisponibleException.class);
        assertThat(contar("usuario")).isZero();
        assertThat(contar("credencial")).isZero();
        assertThat(contar("token_un_uso")).isZero();
        assertThat(auditoria.registros).singleElement().satisfies(r -> {
            assertThat(r.resultado()).isEqualTo("FALLO");
            assertThat(r.detalle()).containsEntry("motivo", "NO_DISPONIBLE");
        });
    }

    @Test
    void creaClientePendienteConCredencialRolYTerminos() {
        var resultado = registro.registrar(solicitud("Maria@Ejemplo.com", CLAVE), CONTEXTO);

        assertThat(resultado).isInstanceOf(ResultadoRegistro.Creada.class);
        UUID id = ((ResultadoRegistro.Creada) resultado).id();
        var fila = jdbc.queryForMap("select correo, estado::text as estado, correo_verificado, acepta_terminos, "
                + "fecha_aceptacion, version_terminos, canal_origen::text as canal, celular from usuario where id = ?", id);
        assertThat(fila).containsEntry("correo", "maria@ejemplo.com").containsEntry("estado", "PENDIENTE_VERIFICACION")
                .containsEntry("correo_verificado", false).containsEntry("acepta_terminos", true)
                .containsEntry("version_terminos", "2026-10").containsEntry("canal", "MARKETPLACE")
                .containsEntry("celular", "+51987654321");
        assertThat(fila.get("fecha_aceptacion")).isNotNull();
        assertThat(credenciales.preverificar(Optional.of(id), new SecretoEfimero(CLAVE)).coincide()).isTrue();
        assertThat(autorizacion.resolver(id).codigosRoles()).containsExactly("CLIENTE");
        assertThat(contar("password_historial")).isEqualTo(1);
        assertThat(auditoria.registros).singleElement().satisfies(r -> {
            assertThat(r.accion()).isEqualTo("USUARIO_CREADO");
            assertThat(r.resultado()).isEqualTo("EXITO");
            assertThat(r.objetivoUsuarioId()).isEqualTo(id);
        });
    }

    @Test
    void correoRepetidoConOtrasMayusculasNoCreaNadaYAuditaSinIdentificarLaCuenta() {
        registro.registrar(solicitud("maria@ejemplo.com", CLAVE), CONTEXTO);
        auditoria.registros.clear();

        var resultado = registro.registrar(solicitud("  MARIA@Ejemplo.com ", CLAVE), CONTEXTO);

        assertThat(resultado).isEqualTo(ResultadoRegistro.Rechazo.CORREO_NO_DISPONIBLE);
        assertThat(contar("usuario")).isEqualTo(1);
        assertThat(contar("credencial")).isEqualTo(1);
        assertThat(contar("usuario_rol")).isEqualTo(1);
        assertThat(contar("token_un_uso")).isEqualTo(1);
        assertThat(correosDeVerificacion()).hasSize(1);
        assertThat(auditoria.registros).singleElement().satisfies(r -> {
            assertThat(r.resultado()).isEqualTo("FALLO");
            assertThat(r.objetivoUsuarioId()).isNull();
            assertThat(r.actorId()).isNull();
            assertThat(r.detalle()).containsEntry("motivo", "CORREO_NO_DISPONIBLE");
        });
    }

    @Test
    void contrasenaFueraDePoliticaRevierteLaCuenta() {
        var corta = registro.registrar(solicitud("corta@ejemplo.com", "corta"), CONTEXTO);
        var personal = registro.registrar(solicitud("personal@ejemplo.com", "Quispe-Rojas-2026!"), CONTEXTO);

        assertThat(corta).isInstanceOfSatisfying(ResultadoRegistro.PoliticaIncumplida.class, p ->
                assertThat(p.reglas()).extracting(ResultadoRegistro.ReglaIncumplida::regla).contains("LONGITUD_MINIMA"));
        assertThat(personal).isInstanceOfSatisfying(ResultadoRegistro.PoliticaIncumplida.class, p ->
                assertThat(p.reglas()).extracting(ResultadoRegistro.ReglaIncumplida::regla)
                        .containsExactly("DATOS_PERSONALES"));
        assertThat(contar("usuario")).isZero();
        assertThat(contar("credencial")).isZero();
        assertThat(contar("usuario_rol")).isZero();
        assertThat(contar("token_un_uso")).isZero();
        assertThat(correosDeVerificacion()).isEmpty();
        assertThat(auditoria.registros).extracting(RegistroSeguro::resultado).containsExactly("FALLO", "FALLO");
    }
}
