package com.marketplace.auth_service.sesion.adapter.out.persistence;

import com.marketplace.auth_service.identidad.adapter.out.crypto.ClaveFirmaRsa;
import com.marketplace.auth_service.identidad.adapter.out.crypto.NimbusFirmadorJws;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteFirmaAcceso;
import com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter;
import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.dto.ContextoSesion;
import com.marketplace.auth_service.sesion.application.dto.DatosIdentidad;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.EfectosCierrePort;
import com.marketplace.auth_service.sesion.application.port.out.FirmadorAccesoPort;
import com.marketplace.auth_service.sesion.application.service.SesionService;
import com.marketplace.auth_service.sesion.domain.exception.AccesoDesconocidoException;
import com.marketplace.auth_service.sesion.domain.model.SesionFamilia;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.nimbusds.jose.JWSObject;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EP-02-T003 sobre PostgreSQL real: apertura con JWT RS256 de Sergio (900 s),
 * refresco opaco de 7 días, resolución por jti, revocaciones, rollback y orden
 * de familias. El coordinador transaccional lo aporta el test con
 * {@code TransactionTemplate}; el HTTP llega en EP-02-T004/T006.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SesionJpaAdapter.class, AbrirSesionPostgresTest.PersistenciaPropia.class})
@Testcontainers
class AbrirSesionPostgresTest {
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
            throw new IllegalStateException("No se pudo preparar el PostgreSQL de prueba de apertura", e);
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
    /** FEAT-12-T2: la auditoría del cierre no interviene en apertura. */
    private static final EfectosCierrePort SIN_CIERRE = (usuarioId, familiaId, contexto, ahora) -> { };

    private static FirmaPort firmaReal() {
        var clave = ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
        return new FirmarAccesoService(new NimbusFirmadorJws(clave), ISSUER, RELOJ);
    }

    @BeforeEach
    void servicioConFirmaReal() {
        sesiones = new SesionService(persistencia, new SesionCryptoAdapter(),
                new PuenteFirmaAcceso(firmaReal()),
                u -> new DatosIdentidad(u, usuario + "@ejemplo.test", "Cliente Prueba",
                        List.of("CLIENTE"), List.of(), true),
                SIN_CIERRE, RELOJ);
        usuario = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?,?,?,?, 'ACTIVO')",
                usuario, usuario + "@ejemplo.test", "Cliente", "Prueba");
    }

    private IdentidadAutorizada identidad() {
        return new IdentidadAutorizada(usuario, "Cliente Prueba", usuario + "@ejemplo.test",
                List.of("CLIENTE"), List.of());
    }

    private ContextoPeticion contexto() {
        return new ContextoPeticion("127.0.0.1", "prueba", UUID.randomUUID());
    }

    @Test
    void abrirEmiteJwtDe900SegundosYRefrescoDe7Dias() throws Exception {
        SesionIniciada iniciada = sesiones.abrir(usuario, identidad(), contexto());

        var payload = JWSObject.parse(iniciada.accessToken()).getPayload().toJSONObject();
        assertThat(payload).containsEntry("iss", ISSUER);
        assertThat(((Number) payload.get("exp")).longValue() - ((Number) payload.get("iat")).longValue())
                .isEqualTo(900);
        assertThat(payload.get("roles")).isEqualTo(List.of("CLIENTE"));
        assertThat(iniciada.expiresIn()).isEqualTo(900);
        assertThat(iniciada.expiraRefrescoEn()).isEqualTo(AHORA.plusSeconds(7 * 24 * 3600));
        assertThat(iniciada.refreshToken()).matches("[A-Za-z0-9_-]{43}");

        UUID jti = UUID.fromString((String) payload.get("jti"));
        assertThat(jdbc.queryForObject("select hash_token from token_refresco where familia_id=?",
                String.class, familiaDe(jti))).isEqualTo(
                        SesionCryptoAdapter.sha256Hex(iniciada.refreshToken()));
        ContextoSesion resuelto = sesiones.resolverAcceso(new AccesoVerificado(usuario, jti));
        assertThat(resuelto.familiaId()).isEqualTo(familiaDe(jti));
        assertThat(iniciada.toString()).doesNotContain(iniciada.refreshToken(), iniciada.accessToken());
    }

    private UUID familiaDe(UUID jti) {
        return jdbc.queryForObject("select familia_id from acceso_sesion where jti=?", UUID.class, jti);
    }

    @Test
    void jtiDesconocidoNoResuelve() {
        sesiones.abrir(usuario, identidad(), contexto());
        assertThatThrownBy(() -> sesiones.resolverAcceso(new AccesoVerificado(usuario, UUID.randomUUID())))
                .isInstanceOf(AccesoDesconocidoException.class);
    }

    @Test
    void revocarOtrasConservaLaActualYRevocarTodasCierraTodo() {
        sesiones.abrir(usuario, identidad(), contexto());
        UUID actual = jdbc.queryForObject("select id from sesion_familia where usuario_id=?", UUID.class, usuario);
        sesiones.abrir(usuario, identidad(), contexto());

        sesiones.revocarOtras(usuario, actual);
        assertThat(jdbc.queryForObject("select revocada from sesion_familia where id=?", Boolean.class, actual))
                .isFalse();
        assertThat(jdbc.queryForObject("select count(*) from sesion_familia where usuario_id=? and revocada",
                Integer.class, usuario)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from token_refresco where familia_id=? and not revocado", Integer.class, actual))
                .isEqualTo(1);

        sesiones.revocarTodas(usuario);
        assertThat(jdbc.queryForObject("select count(*) from sesion_familia where usuario_id=? and not revocada",
                Integer.class, usuario)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where not revocado",
                Integer.class)).isZero();
    }

    @Test
    void familiasSeBloqueanEnOrdenDeUuid() {
        var ids = List.of(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"));
        for (UUID id : ids) {
            persistencia.guardarFamilia(SesionFamilia.nueva(id, usuario, AHORA));
        }
        assertThat(persistencia.listarFamiliasDeUsuario(usuario).stream().map(SesionFamilia::id).toList())
                .containsExactly(ids.get(1), ids.get(2), ids.get(0));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void falloDeFirmaNoDejaTokensHuerfanos() {
        FirmadorAccesoPort firmaCaida = datos -> {
            throw new IllegalStateException("firma caída");
        };
        var sinFirma = new SesionService(persistencia, new SesionCryptoAdapter(), firmaCaida,
                u -> new DatosIdentidad(u, "c@ejemplo.test", "Cliente Prueba",
                        List.of("CLIENTE"), List.of(), true),
                SIN_CIERRE, RELOJ);
        var tx = new TransactionTemplate(transacciones);
        long familiasAntes = contar("sesion_familia");
        long tokensAntes = contar("token_refresco");
        long accesosAntes = contar("acceso_sesion");

        assertThatThrownBy(() -> tx.execute(estado -> sinFirma.abrir(usuario, identidad(), contexto())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(contar("sesion_familia")).isEqualTo(familiasAntes);
        assertThat(contar("token_refresco")).isEqualTo(tokensAntes);
        assertThat(contar("acceso_sesion")).isEqualTo(accesosAntes);
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla, Long.class);
    }
}
