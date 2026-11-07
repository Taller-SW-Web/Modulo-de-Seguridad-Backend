package com.marketplace.auth_service.identidad.adapter.out.capacidad;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RequierePostgres;
import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.adapter.out.crypto.ClaveFirmaRsa;
import com.marketplace.auth_service.identidad.adapter.out.crypto.NimbusFirmadorJws;
import com.marketplace.auth_service.identidad.application.dto.ResultadoIntrospeccion;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.port.in.IntrospeccionPort;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import com.marketplace.auth_service.identidad.application.service.IntrospeccionService;
import com.marketplace.auth_service.sesion.adapter.in.transaction.SesionTransaccional;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteFirmaAcceso;
import com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter;
import com.marketplace.auth_service.sesion.adapter.out.persistence.SesionJpaAdapter;
import com.marketplace.auth_service.sesion.application.dto.DatosIdentidad;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.EfectosCierrePort;
import com.marketplace.auth_service.sesion.application.service.SesionService;
import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Composición real de la introspección con sesiones sobre PostgreSQL (SPEC-17
 * RF-17.6, revisión del PR #59): el {@link SesionTransaccional} de verdad, con
 * su {@code resolverAcceso} MANDATORY detrás del proxy de Spring, y la llamada
 * <b>sin</b> transacción envolvente, como llega desde el controlador.
 *
 * <p>Cuentas y autorización son dobles: aquí se prueba la frontera
 * transaccional con sesiones, no sus reglas. Nunca toca Supabase.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import({SesionJpaAdapter.class, IntrospeccionSesionPostgresTest.Composicion.class})
@RequierePostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IntrospeccionSesionPostgresTest {

    private static final Instant AHORA = Instant.parse("2026-10-21T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final String ISSUER = "https://g7.example.test/api/v1/auth";
    private static final ActorServicio VENTAS =
            new ActorServicio("modulo-ventas", UUID.randomUUID(), Set.of("tokens:introspeccion"));

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.sesion.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.sesion.adapter.out.persistence.repository")
    static class Composicion {

        @Bean
        FirmaPort firmaPort() {
            return new FirmarAccesoService(new NimbusFirmadorJws(
                    ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072))), ISSUER, RELOJ);
        }

        /** El bean de producción: el proxy aplica el MANDATORY de {@code resolverAcceso}. */
        @Bean
        SesionesPort sesionesPort(SesionJpaAdapter persistencia, FirmaPort firma) {
            SesionCryptoAdapter cripto = new SesionCryptoAdapter();
            return new SesionTransaccional(new SesionService(persistencia, cripto, new PuenteFirmaAcceso(firma),
                    u -> new DatosIdentidad(u, u + "@ejemplo.test", "Cliente Prueba", List.of("CLIENTE"), List.of(),
                            true), (usuarioId, familiaId, contexto, ahora) -> { }, RELOJ),
                    persistencia, cripto, u -> { });
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
        String nombre = "introspeccion_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        try (var admin = DriverManager.getConnection(url, usuario, contrasena); var s = admin.createStatement()) {
            s.execute("CREATE DATABASE " + nombre);
        }
        String propia = url.replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
        try (var c = DriverManager.getConnection(propia, usuario, contrasena); var s = c.createStatement()) {
            s.execute(leer("sesion/base-observada/schema.sql"));
            c.setAutoCommit(false); // V102 bloquea tablas: solo dentro de una transacción
            s.execute(Files.readString(Path.of("docs/migraciones-propuestas/V102__sesion_familia_acceso.sql"),
                    StandardCharsets.UTF_8));
            c.commit();
        }
        registro.add("spring.datasource.url", () -> propia);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
    }

    private static String leer(String recurso) throws Exception {
        try (var in = new ClassPathResource(recurso).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Autowired SesionesPort sesiones;
    @Autowired FirmaPort firma;
    @Autowired PlatformTransactionManager gestor;
    @Autowired JdbcTemplate jdbc;

    private IntrospeccionPort introspeccion;
    private UUID usuario;

    @BeforeEach
    void composicion() {
        usuario = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?,?,?,?, 'ACTIVO')",
                usuario, usuario + "@ejemplo.test", "Cliente", "Prueba");
        CuentasPort cuentas = mock(CuentasPort.class);
        when(cuentas.leer(any())).thenAnswer(i -> Optional.of(new CuentaSnapshot(i.getArgument(0), "Cliente",
                "Prueba", usuario + "@ejemplo.test", null, "ACTIVO", "ACTIVO", true, false, false, 0, 0)));
        AutorizacionPort autorizacion = mock(AutorizacionPort.class);
        when(autorizacion.resolver(any())).thenReturn(new PermisosResueltos(Set.of(Rol.CLIENTE), Set.of()));
        introspeccion = new IntrospeccionService(firma,
                new EstadoIdentidadAdapter(sesiones, cuentas, autorizacion, gestor));
    }

    /** Como el login: abrir exige la transacción del coordinador. */
    private SesionIniciada abrir() {
        return new TransactionTemplate(gestor).execute(estado -> sesiones.abrir(usuario,
                new IdentidadAutorizada(usuario, "Cliente Prueba", usuario + "@ejemplo.test", List.of("CLIENTE"),
                        List.of()), new ContextoPeticion("127.0.0.1", "prueba", UUID.randomUUID())));
    }

    @Test
    void unaSesionVigenteSaleActivaSinTransaccionEnvolvente() {
        String acceso = abrir().accessToken();

        assertThat(introspeccion.introspeccionar(VENTAS, acceso))
                .isInstanceOfSatisfying(ResultadoIntrospeccion.Activa.class,
                        activa -> assertThat(activa.sub()).isEqualTo(usuario));
    }

    @Test
    void unaFamiliaRevocadaSaleInactivaTokenRevocadoSinRomperLaRespuesta() {
        String acceso = abrir().accessToken();
        jdbc.update("update sesion_familia set revocada = true, revocada_en = creado_en where usuario_id = ?",
                usuario);

        assertThat(introspeccion.introspeccionar(VENTAS, acceso))
                .isEqualTo(new ResultadoIntrospeccion.Inactiva(ResultadoIntrospeccion.Motivo.TOKEN_REVOCADO));
        // La consulta revertida no deja nada a medias: la siguiente sigue respondiendo.
        assertThat(introspeccion.introspeccionar(VENTAS, acceso))
                .isEqualTo(new ResultadoIntrospeccion.Inactiva(ResultadoIntrospeccion.Motivo.TOKEN_REVOCADO));
    }
}
