package com.marketplace.auth_service.sesion.adapter.out.persistence;

import com.marketplace.auth_service.identidad.adapter.out.crypto.ClaveFirmaRsa;
import com.marketplace.auth_service.identidad.adapter.out.crypto.NimbusFirmadorJws;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteFirmaAcceso;
import com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter;
import com.marketplace.auth_service.sesion.application.dto.CredencialesLogin;
import com.marketplace.auth_service.sesion.application.dto.CuentaLogin;
import com.marketplace.auth_service.sesion.application.dto.DesafioCreado;
import com.marketplace.auth_service.sesion.application.dto.EstadoCuentaLogin;
import com.marketplace.auth_service.sesion.application.dto.DatosIdentidad;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.PrevisionCredencial;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.sesion.application.dto.RevisionCredencial;
import com.marketplace.auth_service.sesion.application.dto.RolesSesion;
import com.marketplace.auth_service.sesion.application.dto.SecretoEfimero;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.port.in.LoginPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.service.LoginService;
import com.marketplace.auth_service.sesion.application.service.SesionService;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Rol;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EP-02-T007: objetivos propuestos de SPEC-05/06 con 50 concurrentes (login
 * p95 &lt;800 ms, refresh p95 &lt;300 ms), medidos sobre PostgreSQL real con
 * firma RS256 real. El login usa dobles declarados para Eva/Juan José/Luis
 * (pendientes) con el resto real: es la línea base del recorrido propio, no
 * acredita la carga con composición real ni cierra H4/H5. Sin DoblesDeRed ni
 * peticiones sueltas: 50 hilos sincronizados por latch, cada uno en su
 * transacción, midiendo de extremo a extremo del caso de uso.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SesionJpaAdapter.class, RendimientoSesionPostgresTest.PersistenciaPropia.class})
@Testcontainers
@RequiereSesionPostgres
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RendimientoSesionPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.marketplace.auth_service.sesion.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.sesion.adapter.out.persistence.repository")
    static class PersistenciaPropia { }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final String ISSUER = "https://g7.example.test/api/v1/auth";
    private static final int CONCURRENTES = 50;

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("sesion/esquema-previo-v102.sql"));
            ScriptUtils.executeSqlScript(conexion,
                    new FileSystemResource("docs/migraciones-propuestas/V102__sesion_familia_acceso.sql"));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar el PostgreSQL de rendimiento", e);
        }
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired SesionJpaAdapter persistencia;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;

    private SesionesPort sesiones;
    private LoginPort login;
    private UUID usuario;

    @BeforeEach
    void servicios() {
        var clave = ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
        FirmaPort firma = new FirmarAccesoService(new NimbusFirmadorJws(clave), ISSUER, RELOJ);
        var crypto = new SesionCryptoAdapter();
        var firmador = new PuenteFirmaAcceso(firma);
        var identidad = (com.marketplace.auth_service.sesion.application.port.out.ProveedorIdentidadPort)
                u -> new DatosIdentidad(u, u + "@ejemplo.test", "Cliente", List.of("CLIENTE"), List.of(), true);
        sesiones = new SesionService(persistencia, crypto, firmador, identidad, RELOJ);
        var cuentas = new CuentasFijas();
        var credenciales = new CredencialesFijas();
        login = new LoginService(cuentas, credenciales, new ProteccionFija(), new MfaFijo(),
                u -> new RolesSesion(Set.of(Rol.CLIENTE), List.of()), sesiones, RELOJ);
        usuario = UUID.randomUUID();
        jdbc.update("insert into usuario(id, correo, nombres, apellidos, estado) values (?,?,?,?, 'ACTIVO')",
                usuario, usuario + "@ejemplo.test", "Cliente", "Prueba");
        cuentas.cuenta = new CuentaLogin(usuario, "Cliente Prueba", usuario + "@ejemplo.test",
                EstadoCuentaLogin.ACTIVA, false);
        credenciales.cuenta = cuentas.cuenta;
    }

    static class CuentasFijas implements com.marketplace.auth_service.sesion.application.port.out.CuentasLoginPort {
        CuentaLogin cuenta;

        @Override public Optional<CuentaLogin> buscarPorCorreo(String correo) {
            return Optional.ofNullable(cuenta).filter(c -> c.correo().equals(correo));
        }

        @Override public Optional<CuentaLogin> bloquear(UUID id) {
            return Optional.ofNullable(cuenta).filter(c -> c.id().equals(id));
        }
    }

    static class CredencialesFijas
            implements com.marketplace.auth_service.sesion.application.port.out.CredencialesLoginPort {
        CuentaLogin cuenta;

        @Override public PrevisionCredencial preverificar(Optional<UUID> u, SecretoEfimero s) {
            boolean ok = u.isPresent() && cuenta != null && u.get().equals(cuenta.id());
            return new PrevisionCredencial(ok, new RevisionCredencial(UUID.randomUUID(), 3));
        }

        @Override public boolean confirmarRevision(RevisionCredencial r) {
            return true;
        }

        @Override public boolean caducada(UUID u, Set<Rol> roles, Instant ahora) {
            return false;
        }
    }

    static class ProteccionFija
            implements com.marketplace.auth_service.sesion.application.port.out.ProteccionLoginPort {
        @Override public boolean loginBloqueado(UUID u) {
            return false;
        }

        @Override public void registrarFallo(UUID u, SecretoEfimero i,
                                             com.marketplace.auth_service.shared.domain.ContextoPeticion c) {
        }

        @Override public void completarLogin(UUID u) {
        }
    }

    static class MfaFijo implements com.marketplace.auth_service.sesion.application.port.out.MfaLoginPort {
        @Override public boolean esEfectivo(boolean bandera, Set<Rol> roles) {
            return false;
        }

        @Override public DesafioCreado crearDesafio(UUID u, RevisionCredencial r) {
            throw new UnsupportedOperationException("sin MFA en rendimiento");
        }
    }

    private List<Long> medir(int hilos, Callable<?> trabajo) throws Exception {
        var salida = new CountDownLatch(1);
        ExecutorService piscina = Executors.newFixedThreadPool(hilos);
        List<Future<Long>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            futuros.add(piscina.submit(() -> {
                salida.await(30, TimeUnit.SECONDS);
                long inicio = System.nanoTime();
                trabajo.call();
                return System.nanoTime() - inicio;
            }));
        }
        salida.countDown();
        List<Long> duraciones = new ArrayList<>();
        for (Future<Long> futuro : futuros) {
            duraciones.add(futuro.get(120, TimeUnit.SECONDS));
        }
        piscina.shutdown();
        return duraciones;
    }

    private static long p95(List<Long> nanos) {
        var ordenadas = new ArrayList<>(nanos);
        ordenadas.sort(Long::compareTo);
        return ordenadas.get(Math.max(0, (int) Math.ceil(0.95 * ordenadas.size()) - 1));
    }

    @Test
    void refreshP95Menor300MsCon50Concurrentes() throws Exception {
        var tx = new TransactionTemplate(transacciones);
        List<String> secretos = tx.execute(estado -> {
            List<String> lista = new ArrayList<>();
            for (int i = 0; i < CONCURRENTES; i++) {
                lista.add(sesiones.abrir(usuario, new IdentidadAutorizada(usuario, "Cliente",
                        usuario + "@ejemplo.test", List.of("CLIENTE"), List.of()),
                        new ContextoPeticion("127.0.0.1", "rendimiento", UUID.randomUUID())).refreshToken());
            }
            return lista;
        });
        var indice = new java.util.concurrent.atomic.AtomicInteger();
        List<Long> duraciones = medir(CONCURRENTES, () -> {
            String secreto = secretos.get(indice.getAndIncrement() % secretos.size());
            var resultado = new TransactionTemplate(transacciones)
                    .execute(e -> sesiones.renovar(new SecretoRefresco(secreto)));
            assertThat(resultado).isInstanceOf(
                    com.marketplace.auth_service.sesion.application.dto.RenovacionResultado.Renovada.class);
            return null;
        });

        long p95ms = p95(duraciones) / 1_000_000;
        System.out.println("REFRESH_P95_MS=" + p95ms);
        assertThat(p95ms).isLessThan(300);
    }

    @Test
    void loginP95Menor800MsCon50ConcurrentesYProveedoresSimulados() throws Exception {
        String correo = usuario + "@ejemplo.test";
        List<Long> duraciones = medir(CONCURRENTES, () -> {
            var resultado = new TransactionTemplate(transacciones).execute(e -> login.ingresar(
                    new CredencialesLogin(correo, new SecretoEfimero("secreta")),
                    new ContextoPeticion("127.0.0.1", "rendimiento", UUID.randomUUID())));
            assertThat(resultado).isInstanceOf(
                    com.marketplace.auth_service.sesion.application.dto.ResultadoLogin.Exito.class);
            return null;
        });

        long p95ms = p95(duraciones) / 1_000_000;
        System.out.println("LOGIN_P95_MS=" + p95ms);
        assertThat(p95ms).isLessThan(800);
    }
}
