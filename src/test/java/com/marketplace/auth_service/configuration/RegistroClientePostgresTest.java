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
import com.marketplace.auth_service.usuario.application.dto.ResultadoReenvio;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.dto.ResultadoVerificacion;
import com.marketplace.auth_service.usuario.application.dto.SolicitudRegistro;
import com.marketplace.auth_service.usuario.application.port.in.RegistroClientePort;
import com.marketplace.auth_service.usuario.application.port.in.VerificacionCorreoPort;
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
 * FEAT-01-T1/T2/T3 con los proveedores reales de cuentas, credenciales (hasher y política),
 * autorización, tokens de un uso, cuota de reenvíos y outbox cifrado sobre el esquema observado;
 * la auditoría se registra en memoria. Cada clase crea su propia base desechable; nunca Supabase.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC", "g7.registro.version-terminos=2026-10",
        "g7.spa.url=https://spa.example.test/"},
        showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({UsuarioConfiguration.class, AutorizacionConfig.class, CredencialesConfig.class, CredencialesJpaAdapter.class,
        TokensUnUsoConfig.class, RegistroClienteConfig.class, VerificacionCorreoConfig.class,
        RegistroClientePostgresTest.Persistencia.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RegistroClientePostgresTest {

    private static final String CLAVE = "Zorzal-Andino-2026!";
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.7", "pruebas", UUID.randomUUID());
    private static final Pattern ENLACE = Pattern.compile("https://spa\\.example\\.test/verificar-correo\\?token=([^&\\s]+)(&canal=[A-Z]+)?");

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
            // Columnas de reintento del outbox y registro_id de auditoría que validan sus entidades.
            ScriptUtils.executeSqlScript(c,
                    new FileSystemResource("docs/migraciones-propuestas/V106__auditoria_registro_id_y_outbox_reintento.sql"));
            s.execute(leer("db/autorizacion/R__catalogo_autorizacion.sql"));
        }
        Path comunes = Files.createTempFile("feat01-t1-comunes-", ".txt");
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

    @Autowired RegistroClientePort registro;
    @Autowired CredencialesPort credenciales;
    @Autowired AutorizacionPort autorizacion;
    @Autowired VerificacionCorreoPort verificacion;
    @Autowired AuditoriaEnMemoria auditoria;
    @Autowired CifradoMensajesPort cifrado;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void vaciar() {
        jdbc.execute("truncate usuario cascade");
        jdbc.execute("truncate outbox");
        jdbc.execute("truncate solicitud_limitada");
        auditoria.registros.clear();
    }

    private UUID registrar(String correo) {
        return ((ResultadoRegistro.Creada) registro.registrar(solicitud(correo, CLAVE), CONTEXTO)).id();
    }

    /** Token claro del último correo de verificación, leído descifrando el outbox como lo haría el buzón. */
    private String tokenDelUltimoCorreo() throws Exception {
        String carga = jdbc.queryForObject("select payload::text from outbox where topico = 'correo.verificacion_correo' "
                + "order by creado_en desc limit 1", String.class);
        var enlace = ENLACE.matcher(descifrar(carga, "contenido"));
        assertThat(enlace.find()).isTrue();
        return URLDecoder.decode(enlace.group(1), StandardCharsets.UTF_8);
    }

    private String estado(UUID id) {
        return jdbc.queryForObject("select estado::text from usuario where id = ?", String.class, id);
    }

    private int eventosCreado() {
        return contar("outbox where topico = 'usuario.creado'");
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
        assertThat(enlace.group(2)).isEqualTo("&canal=MARKETPLACE"); // FEAT-01-T4: se registró por MARKETPLACE
        String claro = URLDecoder.decode(enlace.group(1), StandardCharsets.UTF_8);
        assertThat(TokenUnUsoHashing.sha256Hex(claro)).isEqualTo(token.get("hash_token"));
        assertThat(token.get("hash_token")).isNotEqualTo(claro);
        assertThat(auditoria.registros).allSatisfy(r -> assertThat(r.detalle().values()).doesNotContain(claro));
    }

    @Test
    void elTokenDelCorreoActivaLaCuentaUnaSolaVezYPublicaUsuarioCreado() throws Exception {
        UUID id = registrar("maria@ejemplo.com");
        String token = tokenDelUltimoCorreo();

        assertThat(verificacion.verificar(token, CONTEXTO)).isEqualTo(ResultadoVerificacion.VERIFICADA);
        assertThat(estado(id)).isEqualTo("ACTIVO");
        assertThat(jdbc.queryForObject("select correo_verificado from usuario where id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select usado from token_un_uso where usuario_id = ?", Boolean.class, id)).isTrue();
        String evento = jdbc.queryForObject("select payload::text from outbox where topico = 'usuario.creado'", String.class);
        assertThat(new ObjectMapper().readTree(evento).path("usuarioId").asText()).isEqualTo(id.toString());
        assertThat(evento).contains("CLIENTE").doesNotContain(token, "maria@ejemplo.com");

        assertThat(verificacion.verificar(token, CONTEXTO)).isEqualTo(ResultadoVerificacion.YA_USADA);
        assertThat(verificacion.verificar("token-inventado", CONTEXTO)).isEqualTo(ResultadoVerificacion.INVALIDA);
        assertThat(eventosCreado()).isEqualTo(1);
        assertThat(auditoria.registros).filteredOn(r -> r.accion().equals("USUARIO_VERIFICADO"))
                .extracting(RegistroSeguro::resultado).containsExactly("EXITO", "FALLO");
    }

    @Test
    void tokenVencidoDejaLaCuentaPendienteSinEvento() throws Exception {
        UUID id = registrar("maria@ejemplo.com");
        String token = tokenDelUltimoCorreo();
        // Emitido hace 25 h (ck_token_un_uso_expiracion exige que venza después de crearse).
        jdbc.update("update token_un_uso set creado_en = now() - interval '25 hours', "
                + "expiracion = now() - interval '1 hour' where usuario_id = ?", id);

        assertThat(verificacion.verificar(token, CONTEXTO)).isEqualTo(ResultadoVerificacion.EXPIRADA);
        assertThat(estado(id)).isEqualTo("PENDIENTE_VERIFICACION");
        assertThat(jdbc.queryForObject("select usado from token_un_uso where usuario_id = ?", Boolean.class, id)).isFalse();
        assertThat(eventosCreado()).isZero();
    }

    @Test
    void reenvioInvalidaElEnlaceAnteriorYAdmiteTresPorHora() throws Exception {
        UUID id = registrar("maria@ejemplo.com");
        String primero = tokenDelUltimoCorreo();

        for (int i = 0; i < 3; i++) {
            assertThat(verificacion.reenviar(" MARIA@ejemplo.com", CONTEXTO)).isEqualTo(ResultadoReenvio.Aceptado.ACEPTADO);
        }
        assertThat(verificacion.reenviar("maria@ejemplo.com", CONTEXTO))
                .isInstanceOfSatisfying(ResultadoReenvio.Limitado.class,
                        l -> assertThat(l.reintentarEnSegundos()).isBetween(3500L, 3600L));
        assertThat(correosDeVerificacion()).hasSize(4); // el del registro y los tres reenvíos
        assertThat(contar("token_un_uso where not usado")).isEqualTo(1);
        assertThat(jdbc.queryForList("select clave_hash from solicitud_limitada where tipo = 'REENVIO_VERIFICACION'",
                String.class)).hasSize(3).allSatisfy(h -> assertThat(h).matches("[0-9a-f]{64}").doesNotContain("maria"));

        assertThat(verificacion.verificar(primero, CONTEXTO)).isEqualTo(ResultadoVerificacion.YA_USADA);
        assertThat(verificacion.verificar(tokenDelUltimoCorreo(), CONTEXTO)).isEqualTo(ResultadoVerificacion.VERIFICADA);
        assertThat(estado(id)).isEqualTo("ACTIVO");
    }

    @Test
    void reenvioAUnCorreoSinCuentaRespondeIgualYTambienSeLimita() {
        for (int i = 0; i < 3; i++) {
            assertThat(verificacion.reenviar("nadie@ejemplo.com", CONTEXTO)).isEqualTo(ResultadoReenvio.Aceptado.ACEPTADO);
        }
        assertThat(verificacion.reenviar("nadie@ejemplo.com", CONTEXTO)).isInstanceOf(ResultadoReenvio.Limitado.class);
        assertThat(correosDeVerificacion()).isEmpty();
        assertThat(contar("solicitud_limitada")).isEqualTo(3);
    }

    @Test
    void conLaColaCaidaElReenvioConservaLaCuotaYRespondeIgualExistaONoLaCuenta() throws Exception {
        UUID id = registrar("maria@ejemplo.com");
        String vigente = tokenDelUltimoCorreo();
        jdbc.execute("alter table outbox add constraint feat01_fallo check (topico <> 'correo.verificacion_correo') not valid");

        for (String correo : List.of("maria@ejemplo.com", "nadie@ejemplo.com")) {
            for (int i = 0; i < 3; i++) {
                assertThat(verificacion.reenviar(correo, CONTEXTO)).isEqualTo(ResultadoReenvio.Aceptado.ACEPTADO);
            }
            assertThat(verificacion.reenviar(correo, CONTEXTO)).isInstanceOf(ResultadoReenvio.Limitado.class);
        }
        assertThat(contar("solicitud_limitada where tipo = 'REENVIO_VERIFICACION'")).isEqualTo(6);
        // Los enlaces nuevos se revirtieron con su correo: el del registro sigue siendo el único vigente.
        assertThat(contar("token_un_uso where not usado")).isEqualTo(1);
        assertThat(correosDeVerificacion()).hasSize(1);
        assertThat(verificacion.verificar(vigente, CONTEXTO)).isEqualTo(ResultadoVerificacion.VERIFICADA);
        assertThat(estado(id)).isEqualTo("ACTIVO");
    }

    @Test
    void reenvioAUnaCuentaYaVerificadaNoEnviaNada() throws Exception {
        registrar("maria@ejemplo.com");
        verificacion.verificar(tokenDelUltimoCorreo(), CONTEXTO);

        assertThat(verificacion.reenviar("maria@ejemplo.com", CONTEXTO)).isEqualTo(ResultadoReenvio.Aceptado.ACEPTADO);
        assertThat(correosDeVerificacion()).hasSize(1);
    }

    @Test
    void colaDeCorreoCaidaRevierteLaCuentaSinTokenNiCorreo() {
        jdbc.execute("alter table outbox add constraint feat01_fallo check (topico <> 'correo.verificacion_correo') not valid");

        assertThatThrownBy(() -> registro.registrar(solicitud("maria@ejemplo.com", CLAVE), CONTEXTO))
                .isInstanceOf(VerificacionNoDisponibleException.class);
        assertThat(contar("usuario")).isZero();
        assertThat(contar("credencial")).isZero();
        assertThat(contar("token_un_uso")).isZero();
        // La auditoría en memoria no participa del rollback: la crítica de EXITO real se revertiría con la cuenta.
        assertThat(auditoria.registros).filteredOn(r -> r.resultado().equals("FALLO")).singleElement()
                .satisfies(r -> assertThat(r.detalle()).containsEntry("motivo", "NO_DISPONIBLE"));
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
