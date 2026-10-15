package com.marketplace.auth_service.autorizacion.adapter.in.transaction;

import com.marketplace.auth_service.autorizacion.application.dto.CambioRolCommand;
import com.marketplace.auth_service.autorizacion.application.dto.RegistroRol;
import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiados;
import com.marketplace.auth_service.autorizacion.application.port.in.GestionRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AuditoriaRolesPort;
import com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException;
import com.marketplace.auth_service.autorizacion.domain.exception.GestionRolesNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Frontera transaccional de {@link GestionRolesPort} (ACUERDOS-INTERNOS §3).
 *
 * <ul>
 *   <li>Una transacción {@code REQUIRED} con aislamiento {@code READ_COMMITTED} por
 *       cambio. Los proveedores mutantes ({@code MANDATORY}) se unen a ella; cualquier
 *       excepción revierte asociación, revocación de sesiones, auditoría y outbox juntos.</li>
 *   <li>Un acceso denegado se audita como {@code ACCESO_DENEGADO} <b>después</b> del
 *       rollback, de forma no crítica: si esa auditoría falla, la respuesta sigue
 *       siendo 403 (ESC-11.3).</li>
 *   <li>Un fallo técnico de la base o de la transacción se entrega como
 *       {@link GestionRolesNoDisponibleException} (503), nunca como un cambio a medias.</li>
 * </ul>
 */
public class GestionRolesTransaccional implements GestionRolesPort {

    private static final Logger log = LoggerFactory.getLogger(GestionRolesTransaccional.class);

    private final GestionRolesPort casoDeUso;
    private final TransactionTemplate transaccion;
    private final AuditoriaRolesPort auditoria;
    private final Clock clock;

    public GestionRolesTransaccional(GestionRolesPort casoDeUso, PlatformTransactionManager transacciones,
                                     AuditoriaRolesPort auditoria, Clock clock) {
        this.casoDeUso = casoDeUso;
        this.auditoria = auditoria;
        this.clock = clock;
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public RolesCambiados asignar(CambioRolCommand comando) {
        return enTransaccion(comando, () -> casoDeUso.asignar(comando));
    }

    private RolesCambiados enTransaccion(CambioRolCommand comando, Supplier<RolesCambiados> operacion) {
        try {
            return transaccion.execute(estado -> operacion.get());
        } catch (AccesoDenegadoException e) {
            auditarDenegacion(comando);
            throw e;
        } catch (DataAccessException | TransactionException e) {
            throw new GestionRolesNoDisponibleException("Fallo técnico al cambiar el rol; no quedó ningún cambio", e);
        }
    }

    private void auditarDenegacion(CambioRolCommand comando) {
        try {
            auditoria.registrarNoCritica(new RegistroRol(UUID.randomUUID(), RegistroRol.Accion.ACCESO_DENEGADO,
                    comando.actor().sub(), comando.usuarioObjetivo(), null, clock.instant(), comando.contexto()));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar ACCESO_DENEGADO de {}: {}", comando.actor().sub(),
                    e.getClass().getSimpleName());
        }
    }
}
