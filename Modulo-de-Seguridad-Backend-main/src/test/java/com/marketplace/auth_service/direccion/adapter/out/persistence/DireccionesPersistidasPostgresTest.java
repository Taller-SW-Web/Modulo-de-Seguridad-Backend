package com.marketplace.auth_service.direccion.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.direccion.adapter.out.persistence.adapter.DireccionesPersistidasAdapter;
import com.marketplace.auth_service.direccion.adapter.out.persistence.repository.DireccionJpaRepository;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort.DatosNuevaDireccion;
import com.marketplace.auth_service.direccion.application.port.out.DireccionesPersistidasPort;
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

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import(DireccionesPersistidasPostgresTest.Propia.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DireccionesPersistidasPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.direccion.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.direccion.adapter.out.persistence.repository")
    static class Propia {
        @Bean DireccionesPersistidasAdapter adapter(DireccionJpaRepository repo) { return new DireccionesPersistidasAdapter(repo); }
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

    @Autowired DireccionesPersistidasPort direcciones;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() { jdbc.execute("truncate usuario cascade"); }

    private UUID cuenta() {
        UUID usuarioId = UUID.randomUUID();
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado,creado_en) values (?,?,?,?,?::estado_cuenta,?)",
                usuarioId, "direccion-" + usuarioId + "@test.invalid", "Nombre", "Apellido", "ACTIVO",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        return usuarioId;
    }

    @Test void primeraDireccionEsPredeterminadaYNuevaPredeterminadaReemplazaLaAnterior() {
        UUID usuarioId = cuenta();
        var primera = direcciones.crear(usuarioId, datos("Casa", false));
        assertThat(primera.esPredeterminada()).isTrue();
        var segunda = direcciones.crear(usuarioId, datos("Trabajo", true));
        assertThat(segunda.esPredeterminada()).isTrue();
        var lista = direcciones.listar(usuarioId);
        assertThat(lista).hasSize(2);
        assertThat(lista.stream().filter(d -> d.esPredeterminada())).extracting(d -> d.etiqueta())
                .containsExactly("Trabajo");
    }

    @Test void unaSegundaSinPedirlaConservaLaPredeterminadaYLaListaLaPoneAlPrincipio() {
        UUID usuarioId = cuenta();
        direcciones.crear(usuarioId, datos("Casa", null));
        direcciones.crear(usuarioId, datos("Trabajo", false));
        direcciones.crear(usuarioId, datos("Oficina", false));

        var lista = direcciones.listar(usuarioId);

        assertThat(lista).hasSize(3);
        assertThat(lista.get(0).etiqueta()).isEqualTo("Casa");
        assertThat(lista.get(0).esPredeterminada()).isTrue();
        assertThat(lista.stream().filter(d -> d.esPredeterminada())).hasSize(1);
        Integer predeterminadas = jdbc.queryForObject(
                "select count(*) from direccion where usuario_id = ? and es_predeterminada", Integer.class, usuarioId);
        assertThat(predeterminadas).isEqualTo(1);
    }

    @Test void cadaUsuarioTieneSuPropiaPredeterminadaYSoloVeLasSuyas() {
        UUID ana = cuenta();
        UUID luis = cuenta();
        direcciones.crear(ana, datos("Casa de Ana", null));
        var deLuis = direcciones.crear(luis, datos("Casa de Luis", null));

        assertThat(deLuis.esPredeterminada()).as("la de otro usuario no cuenta").isTrue();
        assertThat(direcciones.listar(ana)).extracting(d -> d.etiqueta()).containsExactly("Casa de Ana");
        assertThat(direcciones.listar(luis)).extracting(d -> d.etiqueta()).containsExactly("Casa de Luis");
    }

    @Test void unaCuentaSinDireccionesListaVacio() {
        assertThat(direcciones.listar(cuenta())).isEmpty();
    }

    @Test void losOpcionalesVaciosSeGuardanComoNullYLosTextosSeRecortan() {
        UUID usuarioId = cuenta();
        var creada = direcciones.crear(usuarioId, new DatosNuevaDireccion("   ", "  Lima ", "Lima", "Miraflores",
                "  Av. Larco 123  ", "", null));

        assertThat(creada.etiqueta()).isNull();
        assertThat(creada.referencia()).isNull();
        assertThat(creada.departamento()).isEqualTo("Lima");
        assertThat(creada.direccion()).isEqualTo("Av. Larco 123");
        Integer vacias = jdbc.queryForObject(
                "select count(*) from direccion where usuario_id = ? and (etiqueta = '' or referencia = '')",
                Integer.class, usuarioId);
        assertThat(vacias).isZero();
    }

    private static DatosNuevaDireccion datos(String etiqueta, Boolean predeterminada) {
        return new DatosNuevaDireccion(etiqueta, "Lima", "Lima", "Miraflores", "Av. Larco 123", null, predeterminada);
    }
}
