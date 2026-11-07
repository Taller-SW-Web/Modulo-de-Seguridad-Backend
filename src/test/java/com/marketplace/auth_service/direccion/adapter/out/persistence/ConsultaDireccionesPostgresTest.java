package com.marketplace.auth_service.direccion.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.direccion.adapter.out.persistence.adapter.DireccionesPersistidasAdapter;
import com.marketplace.auth_service.direccion.adapter.out.persistence.repository.DireccionJpaRepository;
import com.marketplace.auth_service.direccion.application.port.in.ConsultaDireccionesPort;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort.DatosNuevaDireccion;
import com.marketplace.auth_service.direccion.application.port.out.DireccionesPersistidasPort;
import com.marketplace.auth_service.direccion.application.service.ConsultaDireccionesService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * FEAT-04-T2: el puerto exportado {@code ConsultaDireccionesPort} leyendo de PostgreSQL con el esquema real
 * (incluido el índice único de predeterminada), a través del adaptador real de persistencia.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import(ConsultaDireccionesPostgresTest.Propia.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConsultaDireccionesPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.direccion.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.direccion.adapter.out.persistence.repository")
    static class Propia {
        @Bean DireccionesPersistidasAdapter adapter(DireccionJpaRepository repo) { return new DireccionesPersistidasAdapter(repo); }
        @Bean ConsultaDireccionesPort consulta(DireccionesPersistidasPort persistencia) { return new ConsultaDireccionesService(persistencia); }
    }

    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void base(DynamicPropertyRegistry r) {
        PG.start();
        try (var c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             InputStream in = new ClassPathResource("mfa/base-observada/schema.sql").getInputStream();
             var s = c.createStatement()) {
            s.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException(e); }
        r.add("spring.datasource.url", PG::getJdbcUrl); r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired ConsultaDireccionesPort consulta;
    @Autowired DireccionesPersistidasPort direcciones;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() { jdbc.execute("truncate usuario cascade"); }

    private UUID cuenta() {
        UUID usuarioId = UUID.randomUUID();
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado,creado_en) values (?,?,?,?,?::estado_cuenta,?)",
                usuarioId, "consulta-" + usuarioId + "@test.invalid", "Nombre", "Apellido", "ACTIVO",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        return usuarioId;
    }

    private static DatosNuevaDireccion datos(String etiqueta, Boolean predeterminada) {
        return new DatosNuevaDireccion(etiqueta, "Lima", "Lima", "Miraflores", "Av. Larco 123", null, predeterminada);
    }

    @Test void devuelveSoloLasDelUsuarioConLaPredeterminadaPrimero() {
        UUID ana = cuenta();
        UUID luis = cuenta();
        direcciones.crear(ana, datos("Trabajo", null));
        direcciones.crear(ana, datos("Casa", true));
        direcciones.crear(luis, datos("Otra", null));

        var resultado = consulta.consultar(ana);

        assertThat(resultado).extracting(d -> d.etiqueta()).containsExactly("Casa", "Trabajo");
        assertThat(resultado.get(0).esPredeterminada()).isTrue();
        assertThat(resultado.get(1).esPredeterminada()).isFalse();
    }

    @Test void sinDireccionesOCuentaInexistenteDevuelveListaVacia() {
        assertThat(consulta.consultar(cuenta())).isEmpty();
        assertThat(consulta.consultar(UUID.randomUUID())).isEmpty();
    }

    @Test void laListaDevueltaNoSePuedeModificar() {
        UUID ana = cuenta();
        direcciones.crear(ana, datos("Casa", null));

        var resultado = consulta.consultar(ana);

        assertThatThrownBy(resultado::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void noExponeDocumentoNiFechaDeNacimiento() {
        UUID ana = cuenta();
        direcciones.crear(ana, datos("Casa", null));

        var campos = java.util.Arrays.stream(consulta.consultar(ana).get(0).getClass().getRecordComponents())
                .map(c -> c.getName()).toList();

        assertThat(campos).containsExactlyInAnyOrder("id", "etiqueta", "departamento", "provincia", "distrito",
                "direccion", "referencia", "esPredeterminada");
    }
}
