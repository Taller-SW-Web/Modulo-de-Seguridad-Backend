package com.marketplace.auth_service.autorizacion.adapter.out.persistence;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.UsuarioRolJpaEntity;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.configuration.AutorizacionConfig;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guard de administrador y autorización sobre PostgreSQL real (EP-05-T002).
 *
 * <p>Sin transacción de prueba envolvente: cada operación confirma de verdad,
 * igual que en producción, para poder observar el bloqueo entre dos
 * transacciones. Los datos quedan en el esquema aleatorio de esta clase.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AutorizacionConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RequierePostgres
class GuardAdministradorTest extends PostgresDePrueba {

    @Autowired
    private AutorizacionPort autorizacion;
    @Autowired
    private UsuarioRolJpaRepository usuarioRol;
    @Autowired
    private PlatformTransactionManager transacciones;

    private UUID personaCon(Rol... roles) {
        UUID id = UUID.randomUUID();
        for (Rol rol : roles) {
            usuarioRol.save(new UsuarioRolJpaEntity(id, rol));
        }
        return id;
    }

    private static ActorUsuario actor(UUID sub) {
        return new ActorUsuario(sub, UUID.randomUUID(), Set.of(), Set.of());
    }

    @Test
    void sinTransaccionAbiertaElGuardFallaEnVezDeFingirQueBloquea() {
        assertThatThrownBy(() -> autorizacion.bloquearCatalogoAdministrador())
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void elGuardSerializaDosTransaccionesHastaQueLaPrimeraConfirma() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transacciones);
        CountDownLatch primeraTieneElLock = new CountDownLatch(1);
        CountDownLatch soltarPrimera = new CountDownLatch(1);
        AtomicLong segundaObtuvoElLock = new AtomicLong();

        CompletableFuture<Void> primera = CompletableFuture.runAsync(() -> tx.executeWithoutResult(estado -> {
            autorizacion.bloquearCatalogoAdministrador();
            primeraTieneElLock.countDown();
            esperar(soltarPrimera);
        }));
        assertThat(primeraTieneElLock.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> segunda = CompletableFuture.runAsync(() -> tx.executeWithoutResult(estado -> {
            autorizacion.bloquearCatalogoAdministrador();
            segundaObtuvoElLock.set(System.nanoTime());
        }));

        Thread.sleep(700);
        assertThat(segunda).as("la segunda debe esperar mientras la primera tenga el lock").isNotDone();

        long liberado = System.nanoTime();
        soltarPrimera.countDown();
        primera.get(10, TimeUnit.SECONDS);
        segunda.get(10, TimeUnit.SECONDS);

        assertThat(segundaObtuvoElLock.get()).isGreaterThan(liberado);
    }

    @Test
    void devuelveLosUuidDeQuienesTienenElRol() {
        UUID admin1 = personaCon(Rol.ADMIN_SISTEMA);
        UUID admin2 = personaCon(Rol.ADMIN_SISTEMA, Rol.VENDEDOR);
        UUID cliente = personaCon(Rol.CLIENTE);

        assertThat(autorizacion.usuariosConRol(Rol.ADMIN_SISTEMA)).contains(admin1, admin2).doesNotContain(cliente);
        assertThat(autorizacion.usuariosConRol(Rol.GESTOR_COMERCIAL)).doesNotContain(admin1, admin2, cliente);
    }

    @Test
    void lasDecisionesUsanLaBaseYNoLosClaimsDelToken() {
        UUID adminVentas = personaCon(Rol.ADMIN_VENTAS);
        UUID admin = personaCon(Rol.ADMIN_SISTEMA);
        ActorUsuario conClaimFalso = new ActorUsuario(adminVentas, UUID.randomUUID(),
                Set.of("ADMIN_SISTEMA"), Set.of("rol.asignar"));

        assertThatThrownBy(() -> autorizacion.exigirPermiso(conClaimFalso, Permiso.ROL_ASIGNAR))
                .isInstanceOf(AccesoDenegadoException.class);
        autorizacion.exigirPermiso(actor(admin), Permiso.ROL_ASIGNAR);
    }

    @Test
    void retirarElRolQuitaElPermisoEnLaSiguienteDecision() {
        UUID id = personaCon(Rol.ADMIN_SISTEMA);
        autorizacion.exigirPermiso(actor(id), Permiso.ROL_ASIGNAR);

        usuarioRol.deleteById(new UsuarioRolJpaEntity.Id(id, Rol.ADMIN_SISTEMA));

        assertThatThrownBy(() -> autorizacion.exigirPermiso(actor(id), Permiso.ROL_ASIGNAR))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void titularVersusOtraCuenta() {
        UUID cliente = personaCon(Rol.CLIENTE);
        UUID otro = personaCon(Rol.CLIENTE);

        autorizacion.exigirTitularOPrivilegio(actor(cliente), cliente, Permiso.USUARIO_VER);
        assertThatThrownBy(() -> autorizacion.exigirTitularOPrivilegio(actor(cliente), otro, Permiso.USUARIO_VER))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("la prueba no soltó la primera transacción");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
