package com.marketplace.auth_service.credencial.adapter.out.persistence;

import com.marketplace.auth_service.configuration.CredencialesConfig;
import com.marketplace.auth_service.credencial.adapter.out.config.PasswordComunesArchivo;
import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.CredencialJpaEntity;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.*;
import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.credencial.domain.exception.PoliticaNoDisponibleException;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.adapter.in.transaction.CuentasPortTransaccional;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.UsuarioRepositoryAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.*;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.service.ConsultaCuentasService;
import jakarta.persistence.EntityManager;
import java.sql.DriverManager;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CredencialesPortPostgresTest.Config.class, CredencialesConfig.class, CredencialesJpaAdapter.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CredencialesPortPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {CredencialJpaEntity.class, UsuarioJpaEntity.class})
    @EnableJpaRepositories(basePackageClasses = {CredencialJpaRepository.class, UsuarioJpaRepository.class})
    static class Config {
        @Bean CuentasPort cuentas(UsuarioJpaRepository usuarios, UsuarioEstadoEfectivoRepository estados) {
            // Cuenta y persistencia de Eva reales; filtro por rol no se usa en estos recorridos.
            return new CuentasPortTransaccional(new ConsultaCuentasService(
                    new UsuarioRepositoryAdapter(usuarios, estados), rol -> Set.of()));
        }
    }
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17.6");
    static Path comunes;
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        PG.start();
        try (var conexion = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            try (var schema = new ClassPathResource("mfa/base-observada/schema.sql").getInputStream();
                    var statement = conexion.createStatement()) {
                statement.execute(new String(schema.readAllBytes(), StandardCharsets.UTF_8));
            }
            ScriptUtils.executeSqlScript(conexion,
                    new FileSystemResource("docs/migraciones-propuestas/V101__credencial_version.sql"));
            comunes = Files.createTempFile("ep03-comunes-", ".txt");
            Files.writeString(comunes, "ComunDePrueba8!\n");
        } catch (Exception error) { throw new IllegalStateException("Fixture PostgreSQL local no disponible", error); }
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("g7.password.comunes.archivo", () -> comunes.toString());
    }
    @Autowired CredencialesPort puerto;
    @Autowired CuentasPort cuentas;
    @Autowired PasswordHasherPort hasher;
    @Autowired PasswordComunesArchivo lista;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;
    @Autowired EntityManager em;
    @Autowired CredencialJpaRepository credenciales;
    @Autowired ApplicationContext contexto;
    UUID usuario;
    final Instant fecha = Instant.parse("2026-01-01T00:00:00Z");
    final String secreto = "  Cláv3!Exacta  ";

    TransactionTemplate tx() { return new TransactionTemplate(txManager); }
    @BeforeEach void preparar() throws Exception {
        Files.writeString(comunes, "ComunDePrueba8!\n");
        lista.recargar();
        usuario = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?, ?, 'María José', 'García Ruiz', 'ACTIVO')",
                usuario, usuario + "@example.test");
        jdbc.update("insert into credencial(usuario_id, hash_contrasena, ultimo_cambio, version) values (?, ?, ?, 0)",
                usuario, hasher.generar(secreto).valor(), java.sql.Timestamp.from(fecha));
    }
    @AfterAll static void limpiarArchivoTemporal() throws Exception { if (comunes != null) Files.deleteIfExists(comunes); }

    @Test void unSoloBeanProductivoYPreverificacionExactaArgon2() {
        assertThat(contexto.getBeansOfType(CredencialesPort.class)).hasSize(1);
        var resultado = puerto.preverificar(Optional.of(usuario), new SecretoEfimero(secreto));
        assertThat(resultado.coincide()).isTrue();
        assertThat(resultado.revision()).contains(new RevisionCredencial(usuario, usuario, 0));
        assertThat(puerto.preverificar(Optional.of(usuario), new SecretoEfimero(secreto.strip())).coincide()).isFalse();
        assertThat(puerto.preverificar(Optional.empty(), new SecretoEfimero(secreto)).coincide()).isFalse();
    }
    @Test void verificaBCryptCompatibleExistenteSinReescribirHash() {
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(secreto);
        jdbc.update("update credencial set hash_contrasena=? where usuario_id=?", hash, usuario);
        assertThat(puerto.preverificar(Optional.of(usuario), new SecretoEfimero(secreto)).coincide()).isTrue();
        assertThat(jdbc.queryForObject("select hash_contrasena from credencial where usuario_id=?", String.class, usuario))
                .isEqualTo(hash);
    }
    @Test void confirmacionObligaTransaccionYRechazaIdentificadorInventado() {
        var revision = new RevisionCredencial(usuario, usuario, 0);
        assertThatThrownBy(() -> puerto.confirmarRevision(revision))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        tx().executeWithoutResult(status -> {
            cuentas.bloquear(usuario);
            assertThat(puerto.confirmarRevision(revision)).isTrue();
            assertThat(puerto.confirmarRevision(new RevisionCredencial(usuario, UUID.randomUUID(), 0))).isFalse();
        });
    }
    @Test void cambioEntrePreverificacionYBloqueoInvalidaRevisionInclusoConEntidadCacheada() throws Exception {
        var revision = puerto.preverificar(Optional.of(usuario), new SecretoEfimero(secreto)).revision().orElseThrow();
        String nuevoHash = hasher.generar("Nueva8!ExactaZZ").valor();
        try (var ejecutor = Executors.newSingleThreadExecutor()) {
            tx().executeWithoutResult(status -> {
                var cacheada = em.find(CredencialJpaEntity.class, usuario);
                assertThat(cacheada.getVersion()).isZero();
                try {
                    ejecutor.submit(() -> tx().executeWithoutResult(otro -> {
                        cuentas.bloquear(usuario);
                        var entidad = credenciales.findById(usuario).orElseThrow();
                        entidad.reemplazarHash(nuevoHash, fecha.plusSeconds(1).atOffset(ZoneOffset.UTC));
                        credenciales.saveAndFlush(entidad);
                    })).get(15, TimeUnit.SECONDS);
                } catch (Exception fallo) { throw new IllegalStateException(fallo); }
                cuentas.bloquear(usuario);
                assertThat(cacheada.getVersion()).isZero(); // L1 sigue antigua.
                assertThat(puerto.confirmarRevision(revision)).isFalse();
                var actual = puerto.preverificar(Optional.of(usuario), new SecretoEfimero("Nueva8!ExactaZZ"));
                assertThat(actual.coincide()).isTrue();
                assertThat(actual.revision()).contains(new RevisionCredencial(usuario, usuario, 1));
            });
        }
        assertThat(puerto.preverificar(Optional.of(usuario), new SecretoEfimero(secreto)).coincide()).isFalse();
    }
    @Test void caducidadConFechaRealYRolesMultiplesEnElLimite() {
        var limite = fecha.plus(Duration.ofDays(90));
        assertThat(puerto.evaluarCaducidad(usuario, Set.of(Rol.ADMIN_VENTAS), limite.minusNanos(1))).isFalse();
        assertThat(puerto.evaluarCaducidad(usuario, Set.of(Rol.CLIENTE, Rol.ADMIN_VENTAS), limite)).isTrue();
        assertThat(puerto.evaluarCaducidad(usuario, Set.of(Rol.CLIENTE, Rol.VENDEDOR), limite.plusSeconds(1))).isFalse();
    }
    @Test void politicaUsaCuentaRealYRechazaActualAunqueHistorialEsteVacio() {
        assertThat(puerto.validarNueva(usuario, new SecretoEfimero("xxGÁRCÍA8!abcd")).errores())
                .extracting(InfraccionPassword::regla).contains(ReglaPassword.DATOS_PERSONALES);
        assertThat(puerto.validarNueva(usuario, new SecretoEfimero(secreto)).errores())
                .extracting(InfraccionPassword::regla).contains(ReglaPassword.YA_UTILIZADA);
        assertThat(puerto.validarNueva(usuario, new SecretoEfimero("Zq8!ryTuvX")).valida()).isTrue();
    }
    @Test void ventanaIncluyeActualYCuatroAnterioresSinInventarSexta() {
        var passwords = List.of("Antigua0!ZZ", "Antigua1!ZZ", "Antigua2!ZZ", "Antigua3!ZZ", "Antigua4!ZZ");
        for (int i = 0; i < passwords.size(); i++) {
            jdbc.update("insert into password_historial(id, usuario_id, hash_contrasena, creado_en) values (?, ?, ?, ?)",
                    UUID.randomUUID(), usuario, hasher.generar(passwords.get(i)).valor(),
                    java.sql.Timestamp.from(fecha.minusSeconds(100 - i)));
        }
        assertThat(puerto.validarNueva(usuario, new SecretoEfimero(passwords.getFirst())).valida()).isTrue();
        for (String password : passwords.subList(1, 5)) {
            assertThat(puerto.validarNueva(usuario, new SecretoEfimero(password)).errores())
                    .extracting(InfraccionPassword::regla).contains(ReglaPassword.YA_UTILIZADA);
        }
        assertThat(puerto.validarNueva(usuario, new SecretoEfimero(secreto)).errores())
                .extracting(InfraccionPassword::regla).contains(ReglaPassword.YA_UTILIZADA);
    }
    @Test void falloDeRecargaConservaVersionYNoAfectaPreverificacion() throws Exception {
        String version = lista.version().orElseThrow();
        Files.writeString(comunes, "");
        lista.recargar();
        assertThat(lista.version()).contains(version);
        assertThat(puerto.preverificar(Optional.of(usuario), new SecretoEfimero(secreto)).coincide()).isTrue();
        assertThat(puerto.validarNueva(usuario, new SecretoEfimero("ComunDePrueba8!")).errores())
                .extracting(InfraccionPassword::regla).contains(ReglaPassword.CONTRASENA_COMUN);
        var sinLista = new com.marketplace.auth_service.credencial.adapter.out.config.PasswordComunesArchivo(
                comunes.resolveSibling("no-existe-ep03-" + UUID.randomUUID()));
        assertThatThrownBy(() -> sinLista.contiene("nueva")).isInstanceOf(PoliticaNoDisponibleException.class);
    }
}
