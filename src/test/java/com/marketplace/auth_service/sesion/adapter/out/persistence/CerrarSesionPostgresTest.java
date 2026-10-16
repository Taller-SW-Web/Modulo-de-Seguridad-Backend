package com.marketplace.auth_service.sesion.adapter.out.persistence;

import com.marketplace.auth_service.identidad.adapter.out.crypto.ClaveFirmaRsa;
import com.marketplace.auth_service.identidad.adapter.out.crypto.NimbusFirmadorJws;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteFirmaAcceso;
import com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter;
import com.marketplace.auth_service.sesion.application.dto.CierreResultado;
import com.marketplace.auth_service.sesion.application.dto.DatosIdentidad;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.MotivoRechazo;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.service.SesionService;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EP-02-T005 sobre PostgreSQL real: cierre normal, repetido y desconocido con
 * efecto persistido; la sesión independiente sigue renovando y tras el cierre
 * el refresco ya no emite tokens. Distingue cierre de familia del vencimiento
 * del acceso firmado (el JWT sigue verificable por firma hasta su exp).
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SesionJpaAdapter.class, CerrarSesionPostgresTest.PersistenciaPropia.class})
@Testcontainers
class CerrarSesionPostgresTest {
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
            throw new IllegalStateException("No se pudo preparar el PostgreSQL de prueba de cierre", e);
        }
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired SesionJpaAdapter persistencia;
    @Autowired JdbcTemplate jdbc;

    private SesionesPort sesiones;
    private UUID usuario;

    @BeforeEach
    void servicio() {
        var clave = ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
        FirmaPort firma = new FirmarAccesoService(new NimbusFirmadorJws(clave), ISSUER, RELOJ);
        sesiones = new SesionService(persistencia, new SesionCryptoAdapter(),
                new PuenteFirmaAcceso(firma),
                u -> new DatosIdentidad(u, u + "@ejemplo.test", "Cliente Prueba",
                        List.of("CLIENTE"), List.of(), true),
                RELOJ);
        usuario = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?,?,?,?, 'ACTIVO')",
                usuario, usuario + "@ejemplo.test", "Cliente", "Prueba");
    }

    private SesionIniciada abrir() {
        return sesiones.abrir(usuario, new IdentidadAutorizada(usuario, "Cliente Prueba",
                usuario + "@ejemplo.test", List.of("CLIENTE"), List.of()),
                new ContextoPeticion("127.0.0.1", "prueba", UUID.randomUUID()));
    }

    private UUID unicaFamilia() {
        return jdbc.queryForObject("select id from sesion_familia where usuario_id=?", UUID.class, usuario);
    }

    @Test
    void cierreNormalRevocaLaFamilia() {
        SesionIniciada iniciada = abrir();
        UUID familia = unicaFamilia();

        assertThat(sesiones.cerrar(new SecretoRefresco(iniciada.refreshToken())))
                .isEqualTo(new CierreResultado.Cerrada(usuario, familia));
        assertThat(jdbc.queryForObject("select revocada from sesion_familia where id=?", Boolean.class, familia))
                .isTrue();
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where familia_id=? and not revocado",
                Integer.class, familia)).isZero();
    }

    @Test
    void cierreRepetidoYDesconocidoNoMutan() {
        SesionIniciada iniciada = abrir();
        sesiones.cerrar(new SecretoRefresco(iniciada.refreshToken()));
        long revocadas = contar("sesion_familia", "revocada");

        assertThat(sesiones.cerrar(new SecretoRefresco(iniciada.refreshToken())))
                .isEqualTo(new CierreResultado.YaCerrada());
        assertThat(sesiones.cerrar(new SecretoRefresco("inexistente")))
                .isEqualTo(new CierreResultado.YaCerrada());
        assertThat(contar("sesion_familia", "revocada")).isEqualTo(revocadas);
    }

    @Test
    void sesionIndependienteSigueRenovandoTrasElCierre() {
        SesionIniciada primera = abrir();
        SesionIniciada segunda = abrir();
        sesiones.cerrar(new SecretoRefresco(primera.refreshToken()));

        assertThat(sesiones.renovar(new SecretoRefresco(segunda.refreshToken())))
                .isInstanceOf(RenovacionResultado.Renovada.class);
        assertThat(sesiones.renovar(new SecretoRefresco(primera.refreshToken())))
                .isEqualTo(new RenovacionResultado.Rechazada(MotivoRechazo.REFRESCO_REVOCADO,
                        usuario, familiaDe(primera.refreshToken())));
    }

    @Test
    void cierreConRefrescoVencidoCierraLaFamilia() {
        SesionIniciada iniciada = abrir();
        UUID familia = unicaFamilia();
        jdbc.update("update token_refresco set creado_en=?, expiracion=? where usuario_id=?",
                OffsetDateTime.ofInstant(AHORA.minusSeconds(8 * 24 * 3600), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(AHORA.minusSeconds(1), ZoneOffset.UTC), usuario);

        assertThat(sesiones.cerrar(new SecretoRefresco(iniciada.refreshToken())))
                .isEqualTo(new CierreResultado.Cerrada(usuario, familia));
        assertThat(jdbc.queryForObject("select revocada from sesion_familia where id=?", Boolean.class, familia))
                .isTrue();
    }

    private UUID familiaDe(String secreto) {
        String hash = new SesionCryptoAdapter().hash(secreto);
        return jdbc.queryForObject("select familia_id from token_refresco where hash_token=?", UUID.class, hash);
    }

    private long contar(String tabla, String condicion) {
        return jdbc.queryForObject("select count(*) from " + tabla + " where " + condicion, Long.class);
    }
}
