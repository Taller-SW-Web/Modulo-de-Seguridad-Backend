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
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.dto.ResultadoActivacion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAltaPersonal;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.dto.ResultadoVerificacion;
import com.marketplace.auth_service.usuario.application.dto.SolicitudAltaPersonal;
import com.marketplace.auth_service.usuario.application.dto.SolicitudRegistro;
import com.marketplace.auth_service.usuario.application.port.in.ActivacionCuentaPort;
import com.marketplace.auth_service.usuario.application.port.in.AltaPersonalPort;
import com.marketplace.auth_service.usuario.application.port.in.RegistroClientePort;
import com.marketplace.auth_service.usuario.application.port.in.VerificacionCorreoPort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * FEAT-13-T1 (SPEC-03 RF-03.1, RF-03.3 a RF-03.5) con los proveedores reales de cuentas, credenciales
 * (hasher y política), autorización, tokens de un uso y outbox cifrado sobre el esquema observado; la
 * auditoría se registra en memoria. Cada clase crea su propia base desechable; nunca Supabase.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC", "g7.registro.version-terminos=2026-11-v1",
        "g7.spa.url=https://spa.example.test/"},
        showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({UsuarioConfiguration.class, AutorizacionConfig.class, CredencialesConfig.class, CredencialesJpaAdapter.class,
        TokensUnUsoConfig.class, RegistroClienteConfig.class, VerificacionCorreoConfig.class,
        AltaAdministrativaConfig.class, AltaAdministrativaPostgresTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AltaAdministrativaPostgresTest {

    private static final String CLAVE = "Zorzal-Andino-2026!";
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.7", "pruebas", UUID.randomUUID());
    private static final Pattern ENLACE = Pattern.compile("https://spa\\.example\\.test/activar-cuenta\\?token=([^&\\s]+)");

    /** Entidades de los dueños que compone el alta, outbox real y una auditoría en memoria. */
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
        @Bean CifradoMensajesPort cifrado() { return new CifradoMensajesAesGcm("test-feat13", new byte[32]); }
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
        String nombre = "alta_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        try (var admin = DriverManager.getConnection(url, usuario, contrasena); var s = admin.createStatement()) {
            s.execute("CREATE DATABASE " + nombre);
        }
        String propia = url.replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
        try (var c = DriverManager.getConnection(propia, usuario, contrasena); var s = c.createStatement()) {
            s.execute(leer("mfa/base-observada/schema.sql"));
            ScriptUtils.executeSqlScript(c, new FileSystemResource("docs/migraciones-propuestas/V101__credencial_version.sql"));
            ScriptUtils.executeSqlScript(c,
                    new FileSystemResource("docs/migraciones-propuestas/V105__token_un_uso_contexto_id.sql"));
            ScriptUtils.executeSqlScript(c,
                    new FileSystemResource("docs/migraciones-propuestas/V106__auditoria_registro_id_y_outbox_reintento.sql"));
            s.execute(leer("db/autorizacion/R__catalogo_autorizacion.sql"));
        }
        Path comunes = Files.createTempFile("feat13-t1-comunes-", ".txt");
        Files.writeString(comunes, "ComunDePrueba8!\n");
        registro.add("spring.datasource.url", () -> propia);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
        registro.add("g7.password.comunes.archivo", comunes::toString);
        byte[] claveCuota = new byte[32];
        java.util.Arrays.fill(claveCuota, (byte) 7);
        registro.add("AUTH_SOLICITUD_LIMITADA_HMAC_KEY", () -> java.util.Base64.getEncoder().encodeToString(claveCuota));
    }

    private static String leer(String recurso) throws Exception {
        try (var in = new ClassPathResource(recurso).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Autowired AltaPersonalPort alta;
    @Autowired ActivacionCuentaPort activacion;
    @Autowired RegistroClientePort registro;
    @Autowired VerificacionCorreoPort verificacion;
    @Autowired CredencialesPort credenciales;
    @Autowired AutorizacionPort autorizacion;
    @Autowired AuditoriaEnMemoria auditoria;
    @Autowired CifradoMensajesPort cifrado;
    @Autowired MensajeriaService mensajeria;
    @Autowired JdbcTemplate jdbc;

    private ActorUsuario admin;

    @BeforeEach
    void vaciar() {
        jdbc.execute("truncate usuario cascade");
        jdbc.execute("truncate outbox");
        jdbc.execute("truncate solicitud_limitada");
        auditoria.registros.clear();
        admin = cuenta("admin@ejemplo.com", "ADMIN_SISTEMA");
    }

    @AfterEach
    void retirarFallosInducidos() {
        jdbc.execute("alter table outbox drop constraint if exists feat13_fallo");
    }

    /** Cuenta activa con un rol, insertada directamente como la sembraría un despliegue. */
    private ActorUsuario cuenta(String correo, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado, correo_verificado) "
                + "values (?, ?, 'Ada', 'Admin', 'ACTIVO', true)", id, correo);
        jdbc.update("insert into usuario_rol (usuario_id, rol_codigo) values (?, ?::codigo_rol)", id, rol);
        return new ActorUsuario(id, UUID.randomUUID(), Set.of(rol), Set.of());
    }

    private UUID crear(String correo, Rol rol) {
        var resultado = alta.crear(admin, new SolicitudAltaPersonal(correo, "Gina", "Gestora", rol), CONTEXTO);
        return ((ResultadoAltaPersonal.Creada) resultado).id();
    }

    /** Token claro del último correo de activación, leído descifrando el outbox como lo haría el buzón. */
    private String tokenDelUltimoCorreo() throws Exception {
        String carga = jdbc.queryForObject("select payload::text from outbox where topico = 'correo.activacion_cuenta' "
                + "order by creado_en desc limit 1", String.class);
        var enlace = ENLACE.matcher(descifrar(carga, "contenido"));
        assertThat(enlace.find()).isTrue();
        return URLDecoder.decode(enlace.group(1), StandardCharsets.UTF_8);
    }

    private String descifrar(String carga, String campo) throws Exception {
        return cifrado.descifrar(new ObjectMapper().readTree(carga).path(campo).asText());
    }

    private String estado(UUID id) {
        return jdbc.queryForObject("select estado::text from usuario where id = ?", String.class, id);
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla, Integer.class);
    }

    @Test
    void elAltaCreaUnaCuentaPendienteSinContrasenaConSuRolYUnEnlaceDe72HorasSinPublicarUsuarioCreado()
            throws Exception {
        UUID id = crear("Gina@Ejemplo.com", Rol.GESTOR_COMERCIAL);

        var fila = jdbc.queryForMap("select correo, estado::text as estado, correo_verificado, acepta_terminos, "
                + "fecha_aceptacion, version_terminos, celular from usuario where id = ?", id);
        assertThat(fila).containsEntry("correo", "gina@ejemplo.com").containsEntry("estado", "PENDIENTE_VERIFICACION")
                .containsEntry("correo_verificado", false).containsEntry("acepta_terminos", false)
                .containsEntry("fecha_aceptacion", null).containsEntry("version_terminos", null)
                .containsEntry("celular", null);
        assertThat(contar("credencial where usuario_id = '" + id + "'")).isZero();
        assertThat(autorizacion.resolver(id).codigosRoles()).containsExactly("GESTOR_COMERCIAL");

        var token = jdbc.queryForMap("select hash_token, usado, expiracion > now() + interval '71 hours' as vigente, "
                + "expiracion < now() + interval '73 hours' as acotado from token_un_uso "
                + "where usuario_id = ? and proposito = 'ACTIVACION'", id);
        assertThat(token).containsEntry("usado", false).containsEntry("vigente", true).containsEntry("acotado", true);
        String carga = jdbc.queryForObject("select payload::text from outbox where topico = 'correo.activacion_cuenta'",
                String.class);
        assertThat(carga).doesNotContain("gina@ejemplo.com", "activar-cuenta");
        assertThat(descifrar(carga, "destino")).isEqualTo("gina@ejemplo.com");
        String claro = tokenDelUltimoCorreo();
        assertThat(TokenUnUsoHashing.sha256Hex(claro)).isEqualTo(token.get("hash_token"));

        // ESC-03.1: todavía no se anuncia la cuenta, ni con usuario.creado ni con usuario.roles_cambiados.
        assertThat(contar("outbox where topico like 'usuario.%'")).isZero();
        assertThat(auditoria.registros).singleElement().satisfies(r -> {
            assertThat(r.accion()).isEqualTo("USUARIO_CREADO");
            assertThat(r.resultado()).isEqualTo("EXITO");
            assertThat(r.actorId()).isEqualTo(admin.sub().toString());
            assertThat(r.objetivoUsuarioId()).isEqualTo(id);
            assertThat(r.detalle()).containsEntry("via", "ALTA_ADMINISTRATIVA").containsEntry("rol", "GESTOR_COMERCIAL");
            assertThat(r.detalle().values()).doesNotContain(claro);
        });
    }

    @Test
    void laActivacionDefineLaContrasenaAceptaLosTerminosYPublicaUsuarioCreadoConSuRol() throws Exception {
        UUID id = crear("gina@ejemplo.com", Rol.GESTOR_COMERCIAL);
        String token = tokenDelUltimoCorreo();
        auditoria.registros.clear();

        assertThat(activacion.activar(token, CLAVE, CONTEXTO)).isEqualTo(ResultadoActivacion.Estado.ACTIVADA);

        var fila = jdbc.queryForMap("select estado::text as estado, correo_verificado, acepta_terminos, "
                + "fecha_aceptacion, version_terminos from usuario where id = ?", id);
        assertThat(fila).containsEntry("estado", "ACTIVO").containsEntry("correo_verificado", true)
                .containsEntry("acepta_terminos", true).containsEntry("version_terminos", "2026-11-v1");
        assertThat(fila.get("fecha_aceptacion")).isNotNull();
        assertThat(credenciales.preverificar(Optional.of(id), new SecretoEfimero(CLAVE)).coincide()).isTrue();
        assertThat(jdbc.queryForObject("select usado from token_un_uso where usuario_id = ? and proposito = 'ACTIVACION'",
                Boolean.class, id)).isTrue();

        List<Map<String, Object>> publicados = new ArrayList<>();
        assertThat(mensajeria.procesarEventos((tipo, sobre) -> publicados.add(sobre))).isEqualTo(1);
        assertThat(publicados).singleElement().satisfies(sobre -> {
            assertThat(sobre).containsEntry("tipo", "usuario.creado").containsEntry("usuarioId", id.toString());
            assertThat(sobre.get("datos")).isEqualTo(Map.of("roles", List.of("GESTOR_COMERCIAL")));
            assertThat(new ObjectMapper().writeValueAsString(sobre)).doesNotContain(token, "gina@ejemplo.com");
        });
        assertThat(auditoria.registros).singleElement().satisfies(r -> {
            assertThat(r.accion()).isEqualTo("CUENTA_ACTIVADA");
            assertThat(r.resultado()).isEqualTo("EXITO");
            assertThat(r.objetivoUsuarioId()).isEqualTo(id);
        });

        // El enlace es de un solo uso y un token inventado responde igual.
        assertThat(activacion.activar(token, CLAVE, CONTEXTO)).isEqualTo(ResultadoActivacion.Estado.YA_USADA);
        assertThat(activacion.activar("token-inventado", CLAVE, CONTEXTO)).isEqualTo(ResultadoActivacion.Estado.INVALIDA);
        assertThat(contar("outbox where topico = 'usuario.creado'")).isEqualTo(1);
    }

    @Test
    void unaContrasenaFueraDeLaPoliticaDejaLaCuentaPendienteYElEnlaceSigueSirviendo() throws Exception {
        UUID id = crear("gina@ejemplo.com", Rol.VENDEDOR);
        String token = tokenDelUltimoCorreo();

        var rechazo = activacion.activar(token, "vendedor1", CONTEXTO);

        assertThat(rechazo).isInstanceOfSatisfying(ResultadoActivacion.PoliticaIncumplida.class, p ->
                assertThat(p.reglas()).extracting(ResultadoRegistro.ReglaIncumplida::regla)
                        .contains("LONGITUD_MINIMA", "CARACTER_ESPECIAL"));
        assertThat(estado(id)).isEqualTo("PENDIENTE_VERIFICACION");
        assertThat(contar("credencial where usuario_id = '" + id + "'")).isZero();
        assertThat(contar("outbox where topico = 'usuario.creado'")).isZero();
        assertThat(activacion.activar(token, CLAVE, CONTEXTO)).isEqualTo(ResultadoActivacion.Estado.ACTIVADA);
    }

    @Test
    void unaCuentaSinRolSeActivaYSeAnunciaConRolesVacios() throws Exception {
        UUID id = crear("repartidor@ejemplo.com", null);
        assertThat(autorizacion.resolver(id).codigosRoles()).isEmpty();

        assertThat(activacion.activar(tokenDelUltimoCorreo(), CLAVE, CONTEXTO)).isEqualTo(ResultadoActivacion.Estado.ACTIVADA);

        List<Map<String, Object>> publicados = new ArrayList<>();
        mensajeria.procesarEventos((tipo, sobre) -> publicados.add(sobre));
        assertThat(publicados).singleElement().satisfies(sobre ->
                assertThat(sobre.get("datos")).isEqualTo(Map.of("roles", List.of())));
    }

    @Test
    void unEnlaceVencidoResponde410SinActivar() throws Exception {
        UUID id = crear("gina@ejemplo.com", Rol.VENDEDOR);
        String token = tokenDelUltimoCorreo();
        // ck_token_un_uso_expiracion exige expiracion > creado_en: se mueven las dos al pasado.
        jdbc.update("update token_un_uso set creado_en = now() - interval '73 hours', "
                + "expiracion = now() - interval '1 hour' where usuario_id = ?", id);

        assertThat(activacion.activar(token, CLAVE, CONTEXTO)).isEqualTo(ResultadoActivacion.Estado.EXPIRADA);
        assertThat(estado(id)).isEqualTo("PENDIENTE_VERIFICACION");
        assertThat(auditoria.registros).filteredOn(r -> r.accion().equals("CUENTA_ACTIVADA"))
                .singleElement().satisfies(r -> assertThat(r.detalle()).containsEntry("motivo", "ENLACE_EXPIRADO"));
    }

    @Test
    void unCorreoYaRegistradoNoCreaNadaNiPublicaEventos() {
        crear("gina@ejemplo.com", Rol.VENDEDOR);
        int cuentas = contar("usuario");
        int filas = contar("outbox");
        auditoria.registros.clear();

        var resultado = alta.crear(admin, new SolicitudAltaPersonal("GINA@ejemplo.com", "Otra", "Persona",
                Rol.ADMIN_VENTAS), CONTEXTO);

        assertThat(resultado).isEqualTo(ResultadoAltaPersonal.Rechazo.CORREO_NO_DISPONIBLE);
        assertThat(contar("usuario")).isEqualTo(cuentas);
        assertThat(contar("outbox")).isEqualTo(filas);
        assertThat(auditoria.registros).singleElement().satisfies(r -> {
            assertThat(r.resultado()).isEqualTo("FALLO");
            assertThat(r.objetivoUsuarioId()).isNull();
            assertThat(r.detalle()).containsEntry("motivo", "CORREO_NO_DISPONIBLE");
        });
    }

    @Test
    void sinUsuarioCrearSeDeniegaYSeAudita() {
        var vendedor = cuenta("vendedor@ejemplo.com", "VENDEDOR");

        assertThat(alta.autorizar(vendedor, CONTEXTO)).isFalse();
        assertThat(alta.crear(vendedor, new SolicitudAltaPersonal("nuevo@ejemplo.com", "N", "N", Rol.ADMIN_SISTEMA),
                CONTEXTO)).isEqualTo(ResultadoAltaPersonal.Rechazo.SIN_PERMISO);

        assertThat(contar("usuario where correo = 'nuevo@ejemplo.com'")).isZero();
        assertThat(auditoria.registros).extracting(RegistroSeguro::accion).containsExactly("ACCESO_DENEGADO",
                "ACCESO_DENEGADO");
    }

    @Test
    void sinColaDeCorreoElAltaSeRevierteCompleta() {
        jdbc.execute("alter table outbox add constraint feat13_fallo check (topico <> 'correo.activacion_cuenta')");

        assertThatThrownBy(() -> crear("gina@ejemplo.com", Rol.VENDEDOR))
                .isInstanceOf(VerificacionNoDisponibleException.class);

        assertThat(contar("usuario where correo = 'gina@ejemplo.com'")).isZero();
        assertThat(contar("token_un_uso")).isZero();
        assertThat(auditoria.registros).extracting(RegistroSeguro::resultado).contains("FALLO");
    }

    @Test
    void elReenvioDeVerificacionNoActivaUnaCuentaCreadaPorElAdministrador() throws Exception {
        UUID id = crear("gina@ejemplo.com", Rol.VENDEDOR);

        verificacion.reenviar("gina@ejemplo.com", CONTEXTO);

        // Responde 202 como siempre, pero no envía el enlace de verificación que la activaría sin contraseña.
        assertThat(contar("outbox where topico = 'correo.verificacion_correo'")).isZero();
        assertThat(estado(id)).isEqualTo("PENDIENTE_VERIFICACION");
        // Y el registro público sigue recibiéndolo.
        var cliente = registro.registrar(new SolicitudRegistro("cliente@ejemplo.com", "María", "Quispe",
                "+51987654321", "WEB", CLAVE), CONTEXTO);
        assertThat(cliente).isInstanceOf(ResultadoRegistro.Creada.class);
        String carga = jdbc.queryForObject("select payload::text from outbox where topico = 'correo.verificacion_correo'",
                String.class);
        var enlace = Pattern.compile("verificar-correo\\?token=([^&\\s]+)").matcher(descifrar(carga, "contenido"));
        assertThat(enlace.find()).isTrue();
        assertThat(verificacion.verificar(URLDecoder.decode(enlace.group(1), StandardCharsets.UTF_8), CONTEXTO))
                .isEqualTo(ResultadoVerificacion.VERIFICADA);
    }
}
