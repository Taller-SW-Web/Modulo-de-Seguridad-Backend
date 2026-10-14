package com.marketplace.auth_service.autorizacion.adapter.out.persistence;

import com.marketplace.auth_service.autorizacion.adapter.in.transaction.GestionRolesTransaccional;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.UsuarioRolJpaEntity;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.dto.CambioRolCommand;
import com.marketplace.auth_service.autorizacion.application.dto.CuentaRoles;
import com.marketplace.auth_service.autorizacion.application.dto.RegistroRol;
import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiados;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.in.GestionRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AuditoriaRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.CuentasRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.EventosRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.SesionesRolesPort;
import com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException;
import com.marketplace.auth_service.autorizacion.domain.exception.GestionRolesNoDisponibleException;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.configuration.AutorizacionConfig;
import com.marketplace.auth_service.configuration.RolesConfig;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Asignación de roles sobre PostgreSQL real, con la transacción real del
 * decorador (EP-05-T003).
 *
 * <p>Los proveedores de otros dueños aún no existen. Aquí los sustituyen dobles
 * que <b>escriben en PostgreSQL con JDBC dentro de la misma transacción</b>, en
 * tablas {@code prueba_*} de esta clase: la cuenta se bloquea con
 * {@code SELECT ... FOR UPDATE}, y la revocación de sesiones, la auditoría
 * crítica y el outbox insertan filas. Así se observa de verdad que un fallo
 * revierte todo junto. No acreditan los proveedores reales de Eva, Jose Luis y
 * Christian: eso se comprueba en EP-05-T006.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AutorizacionConfig.class, RolesConfig.class, GestionRolesPostgresTest.Proveedores.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RequierePostgres
class GestionRolesPostgresTest extends PostgresDePrueba {

    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("198.51.100.4", "prueba", UUID.randomUUID());

    /** Qué debe fallar en la próxima operación; lo leen los dobles. */
    static final AtomicReference<String> FALLA = new AtomicReference<>();

    @TestConfiguration
    static class Proveedores {

        @Bean
        CuentasRolesPort cuentasRolesPort(JdbcTemplate jdbc) {
            return usuarioId -> {
                exigirTransaccion();
                return jdbc.query("SELECT id, estado FROM prueba_cuenta WHERE id = ? FOR UPDATE",
                        (fila, n) -> new CuentaRoles(fila.getObject("id", UUID.class), "Nombre", "c@ejemplo.test",
                                fila.getString("estado")), usuarioId).stream().findFirst();
            };
        }

        @Bean
        SesionesRolesPort sesionesRolesPort(JdbcTemplate jdbc) {
            return usuarioId -> {
                exigirTransaccion();
                jdbc.update("INSERT INTO prueba_revocacion (usuario_id) VALUES (?)", usuarioId);
            };
        }

        @Bean
        AuditoriaRolesPort auditoriaRolesPort(JdbcTemplate jdbc) {
            return new AuditoriaRolesPort() {
                @Override
                public void registrarCritica(RegistroRol registro) {
                    exigirTransaccion();
                    if ("auditoria".equals(FALLA.get())) {
                        throw new GestionRolesNoDisponibleException("auditoría crítica caída");
                    }
                    insertar(jdbc, registro);
                }

                @Override
                public void registrarNoCritica(RegistroRol registro) {
                    // Fuera de la transacción rechazada: JDBC confirma esta fila sola.
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    insertar(jdbc, registro);
                }
            };
        }

        @Bean
        EventosRolesPort eventosRolesPort(JdbcTemplate jdbc) {
            return evento -> {
                exigirTransaccion();
                if ("outbox".equals(FALLA.get())) {
                    throw new DataIntegrityViolationException("outbox lleno");
                }
                jdbc.update("INSERT INTO prueba_outbox (evento_id, usuario_id, roles) VALUES (?, ?, ?)",
                        evento.eventoId(), evento.usuarioId(), String.join(",", evento.roles().stream()
                                .map(Rol::name).toList()));
            };
        }

        private static void insertar(JdbcTemplate jdbc, RegistroRol r) {
            jdbc.update("INSERT INTO prueba_auditoria (accion, resultado, actor, objetivo, rol, ip) VALUES (?,?,?,?,?,?)",
                    r.accion().name(), r.resultado(), r.actor(), r.objetivo(),
                    r.rol() == null ? null : r.rol().name(), r.contexto().ip());
        }

        private static void exigirTransaccion() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("los proveedores mutantes participan en la transacción del cambio").isTrue();
        }
    }

    @Autowired
    private GestionRolesPort gestionRoles;
    @Autowired
    private AutorizacionPort autorizacion;
    @Autowired
    private UsuarioRolJpaRepository usuarioRol;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transacciones;

    @BeforeEach
    void preparar() {
        FALLA.set(null);
        jdbc.execute("CREATE TABLE IF NOT EXISTS prueba_cuenta (id uuid PRIMARY KEY, estado varchar(30) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS prueba_revocacion (usuario_id uuid NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS prueba_auditoria (accion varchar(30), resultado varchar(10),"
                + " actor uuid, objetivo uuid, rol varchar(30), ip varchar(50))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS prueba_outbox (evento_id uuid, usuario_id uuid, roles text)");
    }

    private UUID cuenta(String estado, Rol... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO prueba_cuenta (id, estado) VALUES (?, ?)", id, estado);
        for (Rol rol : roles) {
            usuarioRol.save(new UsuarioRolJpaEntity(id, rol));
        }
        return id;
    }

    private static CambioRolCommand asignar(UUID actor, UUID objetivo, String rol) {
        return new CambioRolCommand(new ActorUsuario(actor, UUID.randomUUID(), Set.of(), Set.of()), objetivo, rol,
                CONTEXTO);
    }

    private Set<Rol> rolesEnLaBase(UUID id) {
        return Set.copyOf(usuarioRol.rolesDe(id));
    }

    private int filas(String tabla, UUID usuario) {
        String columna = tabla.equals("prueba_auditoria") ? "objetivo" : "usuario_id";
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + tabla + " WHERE " + columna + " = ?",
                Integer.class, usuario);
        return n == null ? 0 : n;
    }

    @Test
    void conTodosLosProveedoresElBeanEsElDecoradorTransaccional() {
        assertThat(gestionRoles).isInstanceOf(GestionRolesTransaccional.class);
    }

    @Test
    void asignaYConfirmaAsociacionRevocacionAuditoriaYEventoJuntos() {
        UUID admin = cuenta("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = cuenta("ACTIVO", Rol.VENDEDOR);

        RolesCambiados resultado = gestionRoles.asignar(asignar(admin, vendedor, "GESTOR_DESPACHO"));

        assertThat(resultado.roles()).containsExactly(Rol.VENDEDOR, Rol.GESTOR_DESPACHO);
        assertThat(rolesEnLaBase(vendedor)).containsExactlyInAnyOrder(Rol.VENDEDOR, Rol.GESTOR_DESPACHO);
        assertThat(filas("prueba_revocacion", vendedor)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT accion, resultado, actor, rol, ip FROM prueba_auditoria WHERE objetivo = ?",
                vendedor)).singleElement().satisfies(fila -> {
                    assertThat(fila.get("accion")).isEqualTo("ROL_ASIGNADO");
                    assertThat(fila.get("resultado")).isEqualTo("EXITO");
                    assertThat(fila.get("actor")).isEqualTo(admin);
                    assertThat(fila.get("rol")).isEqualTo("GESTOR_DESPACHO");
                    assertThat(fila.get("ip")).isEqualTo("198.51.100.4");
                });
        assertThat(jdbc.queryForObject("SELECT roles FROM prueba_outbox WHERE usuario_id = ?", String.class, vendedor))
                .isEqualTo("VENDEDOR,GESTOR_DESPACHO");
    }

    @Test
    void siFallaLaAuditoriaCriticaNoQuedaNiAsociacionNiRevocacion() {
        UUID admin = cuenta("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = cuenta("ACTIVO", Rol.VENDEDOR);
        FALLA.set("auditoria");

        assertThatThrownBy(() -> gestionRoles.asignar(asignar(admin, vendedor, "GESTOR_DESPACHO")))
                .isInstanceOf(GestionRolesNoDisponibleException.class);

        assertThat(rolesEnLaBase(vendedor)).containsExactly(Rol.VENDEDOR);
        assertThat(filas("prueba_revocacion", vendedor)).isZero();
        assertThat(filas("prueba_auditoria", vendedor)).isZero();
        assertThat(filas("prueba_outbox", vendedor)).isZero();
    }

    @Test
    void siFallaElOutboxSeRevierteTodoYSeEntregaComoNoDisponible() {
        UUID admin = cuenta("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = cuenta("ACTIVO", Rol.VENDEDOR);
        FALLA.set("outbox");

        assertThatThrownBy(() -> gestionRoles.asignar(asignar(admin, vendedor, "GESTOR_DESPACHO")))
                .isInstanceOf(GestionRolesNoDisponibleException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);

        assertThat(rolesEnLaBase(vendedor)).containsExactly(Rol.VENDEDOR);
        assertThat(filas("prueba_revocacion", vendedor)).isZero();
        assertThat(filas("prueba_auditoria", vendedor)).isZero();
    }

    @Test
    void unAccesoDenegadoNoCambiaNadaYQuedaAuditadoComoNoCritico() {
        UUID adminVentas = cuenta("ACTIVO", Rol.ADMIN_VENTAS);
        UUID vendedor = cuenta("ACTIVO", Rol.VENDEDOR);

        assertThatThrownBy(() -> gestionRoles.asignar(asignar(adminVentas, vendedor, "ADMIN_SISTEMA")))
                .isInstanceOf(AccesoDenegadoException.class);

        assertThat(rolesEnLaBase(vendedor)).containsExactly(Rol.VENDEDOR);
        assertThat(jdbc.queryForList("SELECT accion, resultado, actor, rol FROM prueba_auditoria WHERE objetivo = ?",
                vendedor)).singleElement().satisfies(fila -> {
                    assertThat(fila.get("accion")).isEqualTo("ACCESO_DENEGADO");
                    assertThat(fila.get("resultado")).isEqualTo("FALLO");
                    assertThat(fila.get("actor")).isEqualTo(adminVentas);
                    assertThat(fila.get("rol")).isNull();
                });
    }

    @Test
    void repetirUnRolNoDuplicaLaFilaNiVuelveARevocar() {
        UUID admin = cuenta("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = cuenta("ACTIVO", Rol.VENDEDOR);

        gestionRoles.asignar(asignar(admin, vendedor, "GESTOR_DESPACHO"));
        RolesCambiados segunda = gestionRoles.asignar(asignar(admin, vendedor, "GESTOR_DESPACHO"));

        assertThat(segunda.huboCambio()).isFalse();
        assertThat(usuarioRol.rolesDe(vendedor)).hasSize(2);
        assertThat(filas("prueba_revocacion", vendedor)).isEqualTo(1);
        assertThat(filas("prueba_outbox", vendedor)).isEqualTo(1);
    }

    @Test
    void siOtraTransaccionLeQuitaElPermisoAlActorMientrasEsperaElLockSeRechaza() throws Exception {
        UUID admin = cuenta("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = cuenta("ACTIVO", Rol.VENDEDOR);
        TransactionTemplate tx = new TransactionTemplate(transacciones);
        CountDownLatch tieneElLock = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);

        // Otra operación toma el guard y, antes de confirmar, retira ADMIN_SISTEMA al actor.
        CompletableFuture<Void> otra = CompletableFuture.runAsync(() -> tx.executeWithoutResult(estado -> {
            autorizacion.bloquearCatalogoAdministrador();
            jdbc.update("DELETE FROM usuario_rol WHERE usuario_id = ? AND rol_codigo = 'ADMIN_SISTEMA'", admin);
            tieneElLock.countDown();
            esperar(soltar);
        }));
        assertThat(tieneElLock.await(10, TimeUnit.SECONDS)).isTrue();

        // La asignación pasa la comprobación inicial (aún ve el rol confirmado) y espera el lock.
        CompletableFuture<RolesCambiados> asignacion = CompletableFuture.supplyAsync(
                () -> gestionRoles.asignar(asignar(admin, vendedor, "GESTOR_DESPACHO")));
        Thread.sleep(700);
        assertThat(asignacion).as("espera el lock del catálogo").isNotDone();

        soltar.countDown();
        otra.get(10, TimeUnit.SECONDS);

        assertThat(asignacion).failsWithin(10, TimeUnit.SECONDS)
                .withThrowableOfType(java.util.concurrent.ExecutionException.class)
                .withCauseInstanceOf(AccesoDenegadoException.class);
        assertThat(rolesEnLaBase(vendedor)).containsExactly(Rol.VENDEDOR);
        assertThat(filas("prueba_revocacion", vendedor)).isZero();
    }

    @Test
    void dosAsignacionesConcurrentesDeRolesDistintosConservanAmbas() throws Exception {
        UUID admin1 = cuenta("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID admin2 = cuenta("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = cuenta("ACTIVO", Rol.VENDEDOR);

        CompletableFuture<RolesCambiados> a = CompletableFuture.supplyAsync(
                () -> gestionRoles.asignar(asignar(admin1, vendedor, "GESTOR_DESPACHO")));
        CompletableFuture<RolesCambiados> b = CompletableFuture.supplyAsync(
                () -> gestionRoles.asignar(asignar(admin2, vendedor, "GESTOR_COMERCIAL")));
        a.get(20, TimeUnit.SECONDS);
        b.get(20, TimeUnit.SECONDS);

        assertThat(rolesEnLaBase(vendedor))
                .containsExactlyInAnyOrder(Rol.VENDEDOR, Rol.GESTOR_DESPACHO, Rol.GESTOR_COMERCIAL);
        List<String> eventos = jdbc.queryForList("SELECT roles FROM prueba_outbox WHERE usuario_id = ?", String.class,
                vendedor);
        assertThat(eventos).hasSize(2)
                .as("el último evento confirmado lleva los tres roles: la lista completa, no el cambio")
                .contains("VENDEDOR,GESTOR_DESPACHO,GESTOR_COMERCIAL");
    }

    @Test
    void unaCuentaQueNoExisteNoDejaFilas() {
        UUID admin = cuenta("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID inexistente = UUID.randomUUID();

        assertThatThrownBy(() -> gestionRoles.asignar(asignar(admin, inexistente, "VENDEDOR")))
                .isInstanceOf(com.marketplace.auth_service.autorizacion.domain.exception.UsuarioNoEncontradoException.class);
        assertThat(rolesEnLaBase(inexistente)).isEmpty();
        assertThat(Optional.ofNullable(jdbc.queryForObject("SELECT count(*) FROM prueba_revocacion WHERE usuario_id = ?",
                Integer.class, inexistente))).contains(0);
    }

    private static void esperar(CountDownLatch senal) {
        try {
            senal.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
