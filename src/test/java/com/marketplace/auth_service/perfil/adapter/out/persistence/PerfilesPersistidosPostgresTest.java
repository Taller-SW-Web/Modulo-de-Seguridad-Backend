package com.marketplace.auth_service.perfil.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.perfil.adapter.out.persistence.adapter.PerfilesPersistidosAdapter;
import com.marketplace.auth_service.perfil.adapter.out.persistence.repository.PerfilClienteJpaRepository;
import com.marketplace.auth_service.perfil.adapter.out.persistence.repository.PerfilVendedorJpaRepository;
import com.marketplace.auth_service.perfil.application.port.out.PerfilesPersistidosPort;
import com.marketplace.auth_service.perfil.domain.model.TipoDocumento;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.LocalDate;
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
 * Persistencia de perfil contra PostgreSQL real (esquema observado, ddl-auto=validate: valida los mappings).
 * Sin Docker, Testcontainers la marca como omitida.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuthServiceApplication.class)
@Import(PerfilesPersistidosPostgresTest.Propia.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PerfilesPersistidosPostgresTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.perfil.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.perfil.adapter.out.persistence.repository")
    static class Propia {
        @Bean
        PerfilesPersistidosAdapter perfilesPersistidosAdapter(
                PerfilClienteJpaRepository clientes, PerfilVendedorJpaRepository vendedores) {
            return new PerfilesPersistidosAdapter(clientes, vendedores);
        }
    }

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17.6");

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry r) {
        PG.start();
        try (var c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var in = new ClassPathResource("mfa/base-observada/schema.sql").getInputStream();
                var s = c.createStatement()) {
            s.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired PerfilesPersistidosPort perfiles;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("truncate usuario cascade");
    }

    private UUID usuario(String correo) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado,creado_en) values (?,?,?,?,?::estado_cuenta,?)",
                id, correo, "Nombre", "Apellido", "ACTIVO", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        return id;
    }

    @Test
    void usuarioSinFilasDePerfilDevuelveAusente() {
        UUID id = usuario("sin-perfil@ejemplo.test");

        assertThat(perfiles.clienteDe(id)).isEmpty();
        assertThat(perfiles.vendedorDe(id)).isEmpty();
    }

    @Test
    void clienteSinDocumentoNiFechaSeLeeConAtributosVacios() {
        UUID id = usuario("cliente-vacio@ejemplo.test");
        jdbc.update("insert into perfil_cliente(usuario_id) values (?)", id);

        var perfil = perfiles.clienteDe(id).orElseThrow();

        assertThat(perfil.usuarioId()).isEqualTo(id);
        assertThat(perfil.fechaNacimiento()).isEmpty();
        assertThat(perfil.documento()).isEmpty();
    }

    @Test
    void clienteConDocumentoCifradoLoEntregaCompletoSinTocarlo() {
        UUID id = usuario("cliente-doc@ejemplo.test");
        String hmac = "cd".repeat(32);
        jdbc.update("insert into perfil_cliente(usuario_id,tipo_documento,documento_cifrado,fecha_nacimiento,"
                + "documento_key_id,documento_hmac) values (?,?::tipo_documento,?,?,?,?)",
                id, "PASAPORTE", "valor-cifrado-opaco", LocalDate.of(1990, 1, 31), "key-2026", hmac);

        var perfil = perfiles.clienteDe(id).orElseThrow();

        assertThat(perfil.fechaNacimiento()).contains(LocalDate.of(1990, 1, 31));
        var doc = perfil.documento().orElseThrow();
        assertThat(doc.tipo()).isEqualTo(TipoDocumento.PASAPORTE);
        assertThat(doc.cifrado()).isEqualTo("valor-cifrado-opaco");
        assertThat(doc.keyId()).isEqualTo("key-2026");
        assertThat(doc.hmac()).isEqualTo(hmac);
    }

    @Test
    void vendedorSeLeeConNulosComoSinRegistrar() {
        UUID id = usuario("vendedor@ejemplo.test");
        jdbc.update("insert into perfil_vendedor(usuario_id,codigo_vendedor,tienda) values (?,?,?)",
                id, "V-001", "Tienda Centro");

        var perfil = perfiles.vendedorDe(id).orElseThrow();

        assertThat(perfil.codigoVendedor()).contains("V-001");
        assertThat(perfil.tienda()).contains("Tienda Centro");
        assertThat(perfil.fechaIngreso()).isEmpty();
    }

    @Test
    void unPerfilNoApareceComoDeOtroUsuario() {
        UUID a = usuario("a@ejemplo.test");
        UUID b = usuario("b@ejemplo.test");
        jdbc.update("insert into perfil_vendedor(usuario_id,codigo_vendedor) values (?,?)", a, "V-A");

        assertThat(perfiles.vendedorDe(b)).isEmpty();
        assertThat(perfiles.vendedorDe(a)).isPresent();
    }
}
