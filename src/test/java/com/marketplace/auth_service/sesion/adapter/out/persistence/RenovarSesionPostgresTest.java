package com.marketplace.auth_service.sesion.adapter.out.persistence;

import com.marketplace.auth_service.identidad.adapter.out.crypto.ClaveFirmaRsa;
import com.marketplace.auth_service.identidad.adapter.out.crypto.NimbusFirmadorJws;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteFirmaAcceso;
import com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter;
import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.dto.DatosIdentidad;
import com.marketplace.auth_service.sesion.application.dto.MotivoRechazo;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.ProveedorIdentidadPort;
import com.marketplace.auth_service.sesion.application.service.SesionService;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EP-02-T004 sobre PostgreSQL real: rotación atómica, reúso que revoca solo su
 * familia y dos renovaciones simultáneas con una sola rotación. La identidad
 * (Eva/Luis) usa doble declarado; la composición real llega en T007.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SesionJpaAdapter.class, RenovarSesionPostgresTest.PersistenciaPropia.class})
@Testcontainers
class RenovarSesionPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.sesion.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.sesion.adapter.out.persistence.repository")
    static class PersistenciaPropia { }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final String ISSUER = "https://g7.example.test/api/v1/auth";

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            BaseSesionPostgres.cargarEsquema(conexion);
            BaseSesionPostgres.migrar(conexion);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar el PostgreSQL de prueba de renovación", e);
        }
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired SesionJpaAdapter persistencia;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;

    private SesionesPort sesiones;
    private UUID usuario;
    private final ProveedorIdentidadPort identidadPermite = u -> new DatosIdentidad(u,
            u + "@ejemplo.test", "Cliente Prueba", List.of("CLIENTE"), List.of(), true);

    @BeforeEach
    void servicio() {
        var clave = ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
        FirmaPort firma = new FirmarAccesoService(new NimbusFirmadorJws(clave), ISSUER, RELOJ);
        sesiones = new SesionService(persistencia, new SesionCryptoAdapter(),
                new PuenteFirmaAcceso(firma), identidadPermite, RELOJ);
        usuario = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?,?,?,?, 'ACTIVO')",
                usuario, usuario + "@ejemplo.test", "Cliente", "Prueba");
    }

    private SesionIniciada abrir() {
        return sesiones.abrir(usuario, new IdentidadAutorizada(usuario, "Cliente Prueba",
                usuario + "@ejemplo.test", List.of("CLIENTE"), List.of()),
                new ContextoPeticion("127.0.0.1", "prueba", UUID.randomUUID()));
    }

    private UUID familiaDe(UUID jtiAcceso) {
        return jdbc.queryForObject("select familia_id from acceso_sesion where jti=?", UUID.class, jtiAcceso);
    }

    private UUID ultimoAcceso() {
        return jdbc.queryForObject("select jti from acceso_sesion where usuario_id=? order by creado_en desc, jti desc limit 1",
                UUID.class, usuario);
    }

    @Test
    void rotacionEmiteSucesorYRetieneUsado() {
        SesionIniciada primera = abrir();
        var resultado = sesiones.renovar(new SecretoRefresco(primera.refreshToken()));

        assertThat(resultado).isInstanceOf(RenovacionResultado.Renovada.class);
        var renovada = ((RenovacionResultado.Renovada) resultado).sesion();
        assertThat(renovada.refreshToken()).isNotEqualTo(primera.refreshToken());
        assertThat(contar("token_refresco")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where usuario_id=? and usado", Integer.class, usuario))
                .isEqualTo(1);
        assertThat(contar("acceso_sesion")).isEqualTo(2);
        var resuelto = sesiones.resolverAcceso(new AccesoVerificado(usuario, ultimoAcceso()));
        assertThat(resuelto.familiaId()).isEqualTo(familiaDe(ultimoAcceso()));
        assertThat(renovada.toString()).doesNotContain(renovada.refreshToken(), renovada.accessToken());
    }

    @Test
    void reusoRevocaSuFamiliaSinTocarOtras() {
        SesionIniciada primera = abrir();
        UUID familiaA = familiaDelRefresco(primera.refreshToken());
        SesionIniciada segunda = abrir();
        UUID familiaB = familiaDelRefresco(segunda.refreshToken());

        assertThat(sesiones.renovar(new SecretoRefresco(primera.refreshToken())))
                .isInstanceOf(RenovacionResultado.Renovada.class);
        var resultado = sesiones.renovar(new SecretoRefresco(primera.refreshToken()));

        assertThat(resultado).isEqualTo(new RenovacionResultado.Rechazada(
                MotivoRechazo.REUSO_DETECTADO, usuario, familiaA));
        assertThat(jdbc.queryForObject("select revocada from sesion_familia where id=?", Boolean.class, familiaA))
                .isTrue();
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where familia_id=? and not revocado",
                Integer.class, familiaA)).isZero();
        assertThat(jdbc.queryForObject("select revocada from sesion_familia where id=?", Boolean.class, familiaB))
                .isFalse();
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where familia_id=? and not revocado",
                Integer.class, familiaB)).isEqualTo(1);
    }

    @Test
    void desconocidoNoMutaNada() {
        abrir();
        long tokens = contar("token_refresco");
        assertThat(sesiones.renovar(new SecretoRefresco("inexistente")))
                .isEqualTo(new RenovacionResultado.Rechazada(MotivoRechazo.REFRESCO_DESCONOCIDO, null, null));
        assertThat(contar("token_refresco")).isEqualTo(tokens);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosRenovacionesSimultaneasProducenUnaSolaRotacion() throws Exception {
        SesionIniciada primera;
        var preparacion = new TransactionTemplate(transacciones);
        primera = preparacion.execute(estado -> abrir());
        String secreto = primera.refreshToken();
        var salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        var tarea = (java.util.concurrent.Callable<RenovacionResultado>) () -> {
            salida.await(10, TimeUnit.SECONDS);
            return new TransactionTemplate(transacciones)
                    .execute(estado -> sesiones.renovar(new SecretoRefresco(secreto)));
        };
        Future<RenovacionResultado> uno = hilos.submit(tarea);
        Future<RenovacionResultado> otro = hilos.submit(tarea);
        salida.countDown();
        RenovacionResultado r1 = uno.get(30, TimeUnit.SECONDS);
        RenovacionResultado r2 = otro.get(30, TimeUnit.SECONDS);
        hilos.shutdown();

        assertThat(List.of(r1, r2)).hasSize(2);
        long renovadas = List.of(r1, r2).stream()
                .filter(r -> r instanceof RenovacionResultado.Renovada).count();
        assertThat(renovadas).isEqualTo(1);
        assertThat(List.of(r1, r2).stream()
                .filter(RenovacionResultado.Rechazada.class::isInstance)
                .map(r -> ((RenovacionResultado.Rechazada) r).motivo()))
                .containsExactly(MotivoRechazo.REUSO_DETECTADO);
        assertThat(contar("token_refresco")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select revocada from sesion_familia where usuario_id=?", Boolean.class, usuario)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where usuario_id=? and not revocado", Integer.class, usuario))
                .isZero();
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla + " where usuario_id=?", Long.class, usuario);
    }

    private UUID familiaDelRefresco(String secreto) {
        return jdbc.queryForObject("select familia_id from token_refresco where hash_token=?",
                UUID.class, SesionCryptoAdapter.sha256Hex(secreto));
    }
}
