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
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.MotivoRechazo;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.FirmadorAccesoPort;
import com.marketplace.auth_service.sesion.application.port.out.ProveedorIdentidadPort;
import com.marketplace.auth_service.sesion.application.service.SesionService;
import com.marketplace.auth_service.sesion.domain.exception.FamiliaRevocadaException;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.nimbusds.jose.JWSAlgorithm;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EP-02-T007 sobre PostgreSQL real, con commits reales (cada caso abre su
 * transacción coordinada y luego verifica el estado persistido): el reúso
 * persiste la revocación aunque la respuesta sea 401 (el cuerpo 401 se prueba
 * en {@code SesionHttpTest}), el fallo de firma revierte sin huérfanos y el
 * JWT cumple firma/claims/vigencia sin respuestas prefabricadas. La identidad
 * (Eva/Luis) usa doble declarado e identificado como aislado; la composición
 * con proveedores reales llega cuando existan sus puertos.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SesionJpaAdapter.class, ComposicionSesionPostgresTest.PersistenciaPropia.class})
@Testcontainers
@RequiereSesionPostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ComposicionSesionPostgresTest {
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
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("sesion/esquema-previo-v102.sql"));
            ScriptUtils.executeSqlScript(conexion,
                    new FileSystemResource("docs/migraciones-propuestas/V102__sesion_familia_acceso.sql"));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar el PostgreSQL de composición", e);
        }
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired SesionJpaAdapter persistencia;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;

    private FirmaPort firma;
    private final AtomicReference<FirmadorAccesoPort> firmador =
            new AtomicReference<>();
    private SesionesPort sesiones;
    private UUID usuario;
    private final ProveedorIdentidadPort identidadPermite = u -> new DatosIdentidad(u,
            u + "@ejemplo.test", "Cliente Prueba", List.of("CLIENTE"), List.of(), true);

    @BeforeEach
    void servicio() {
        var clave = ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
        firma = new FirmarAccesoService(new NimbusFirmadorJws(clave), ISSUER, RELOJ);
        firmador.set(new PuenteFirmaAcceso(firma));
        sesiones = new SesionService(persistencia, new SesionCryptoAdapter(), firmador.get(),
                identidadPermite, RELOJ);
        usuario = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?,?,?,?, 'ACTIVO')",
                usuario, usuario + "@ejemplo.test", "Cliente", "Prueba");
    }

    private SesionIniciada abrir() {
        return new TransactionTemplate(transacciones).execute(estado -> sesiones.abrir(usuario,
                new IdentidadAutorizada(usuario, "Cliente Prueba", usuario + "@ejemplo.test",
                        List.of("CLIENTE"), List.of()),
                new ContextoPeticion("127.0.0.1", "prueba", UUID.randomUUID())));
    }

    @Test
    void reusoConfirmaRevocacionPeseAlRechazo() {
        SesionIniciada primera = abrir();
        var tx = new TransactionTemplate(transacciones);
        var renovada = tx.execute(estado -> sesiones.renovar(new SecretoRefresco(primera.refreshToken())));
        assertThat(renovada).isInstanceOf(RenovacionResultado.Renovada.class);

        var reuso = tx.execute(estado -> sesiones.renovar(new SecretoRefresco(primera.refreshToken())));
        assertThat(reuso).isInstanceOf(RenovacionResultado.Rechazada.class);
        assertThat(((RenovacionResultado.Rechazada) reuso).motivo()).isEqualTo(MotivoRechazo.REUSO_DETECTADO);

        assertThat(jdbc.queryForObject("select count(*) from sesion_familia where revocada", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from token_refresco where not revocado", Integer.class))
                .isZero();
    }

    @Test
    void falloDeFirmaEnRotacionRevierteSinHuerfanosNiMedias() {
        SesionIniciada primera = abrir();
        firmador.set(datos -> {
            throw new IllegalStateException("firma caída");
        });
        var conFallo = new SesionService(persistencia, new SesionCryptoAdapter(), firmador.get(),
                identidadPermite, RELOJ);
        var tx = new TransactionTemplate(transacciones);

        assertThatThrownBy(() -> tx.execute(estado -> conFallo.renovar(new SecretoRefresco(primera.refreshToken()))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from token_refresco", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select usado from token_refresco", Boolean.class)).isFalse();

        firmador.set(new PuenteFirmaAcceso(firma));
        var sinFallo = new SesionService(persistencia, new SesionCryptoAdapter(), firmador.get(),
                identidadPermite, RELOJ);
        var recuperada = tx.execute(estado -> sinFallo.renovar(new SecretoRefresco(primera.refreshToken())));
        assertThat(recuperada).isInstanceOf(RenovacionResultado.Renovada.class);
        assertThat(jdbc.queryForObject("select count(*) from token_refresco", Integer.class)).isEqualTo(2);
    }

    @Test
    void jwtCumpleFirmaClaimsYVigenciaReales() throws Exception {
        SesionIniciada iniciada = abrir();

        JWSObject jws = JWSObject.parse(iniciada.accessToken());
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jws.getHeader().getKeyID()).isNotBlank();
        Map<String, Object> claims = jws.getPayload().toJSONObject();
        assertThat(claims.get("iss")).isEqualTo(ISSUER);
        assertThat(UUID.fromString((String) claims.get("sub"))).isEqualTo(usuario);
        assertThat(UUID.fromString((String) claims.get("jti"))).isNotNull();
        assertThat(claims.get("tipo")).isEqualTo("acceso");
        assertThat(claims.get("roles")).isEqualTo(List.of("CLIENTE"));
        assertThat(claims.get("permisos")).isEqualTo(List.of());
        long iat = ((Number) claims.get("iat")).longValue();
        long exp = ((Number) claims.get("exp")).longValue();
        assertThat(exp - iat).isEqualTo(900);
        var verificado = firma.validar(iniciada.accessToken());
        assertThat(verificado.sub()).isEqualTo(usuario);
    }

    @Test
    void revocarNoInvalidaElAccesoYaFirmadoEnElConsumidor() throws Exception {
        SesionIniciada iniciada = abrir();
        UUID jti = UUID.fromString((String) JWSObject.parse(iniciada.accessToken()).getPayload()
                .toJSONObject().get("jti"));
        new TransactionTemplate(transacciones).execute(estado -> {
            sesiones.revocarTodas(usuario);
            return null;
        });

        assertThat(jdbc.queryForObject("select revocada from sesion_familia", Boolean.class)).isTrue();
        var verificado = firma.validar(iniciada.accessToken());
        assertThat(verificado.sub()).isEqualTo(usuario);
        var tx = new TransactionTemplate(transacciones);
        assertThatThrownBy(() -> tx.execute(estado -> sesiones.resolverAcceso(new AccesoVerificado(usuario, jti))))
                .isInstanceOf(FamiliaRevocadaException.class);
    }
}
