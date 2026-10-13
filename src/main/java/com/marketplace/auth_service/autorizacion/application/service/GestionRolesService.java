package com.marketplace.auth_service.autorizacion.application.service;

import com.marketplace.auth_service.autorizacion.application.dto.CambioRolCommand;
import com.marketplace.auth_service.autorizacion.application.dto.CuentaRoles;
import com.marketplace.auth_service.autorizacion.application.dto.RegistroRol;
import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiados;
import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiadosEvento;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.in.GestionRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AsociacionesRolPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AuditoriaRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.CuentasRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.EventosRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.SesionesRolesPort;
import com.marketplace.auth_service.autorizacion.domain.exception.UsuarioNoEncontradoException;
import com.marketplace.auth_service.autorizacion.domain.model.Permiso;
import com.marketplace.auth_service.autorizacion.domain.model.Rol;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Coordinador del cambio de roles (ACUERDOS-INTERNOS §4, «Cambio rol»).
 *
 * <p>No sabe de transacciones: lo envuelve {@code GestionRolesTransaccional},
 * que abre una sola transacción por llamada. Dentro de ella respeta el orden
 * único de locks de §3:
 *
 * <ol>
 *   <li>Comprueba {@code rol.asignar} con los permisos <b>actuales</b> de la base,
 *       antes de validar el código o buscar la cuenta (403 primero, sin revelar nada).</li>
 *   <li>Valida el código de rol (400 si no es uno de los seis).</li>
 *   <li>Nivel 1: lock de la fila {@code rol.ADMIN_SISTEMA}.</li>
 *   <li>Nivel 2: lock de la cuenta objetivo (Eva). Si no existe, 404.</li>
 *   <li>Reautoriza bajo lock: el permiso pudo retirarse mientras esperaba.</li>
 *   <li>Cambia la asociación propia ({@code usuario_rol}).</li>
 *   <li>Nivel 3: revoca todas las familias del objetivo (Jose Luis).</li>
 *   <li>Auditoría crítica y evento con la lista completa resultante, en el mismo commit (Christian).</li>
 * </ol>
 *
 * <p>Si el rol ya estaba asignado no hay cambio: responde con los roles
 * actuales sin revocar sesiones, auditar ni publicar.
 */
public class GestionRolesService implements GestionRolesPort {

    private final AutorizacionPort autorizacion;
    private final AsociacionesRolPort asociaciones;
    private final CuentasRolesPort cuentas;
    private final SesionesRolesPort sesiones;
    private final AuditoriaRolesPort auditoria;
    private final EventosRolesPort eventos;
    private final Clock clock;

    public GestionRolesService(AutorizacionPort autorizacion, AsociacionesRolPort asociaciones,
                               CuentasRolesPort cuentas, SesionesRolesPort sesiones,
                               AuditoriaRolesPort auditoria, EventosRolesPort eventos, Clock clock) {
        this.autorizacion = Objects.requireNonNull(autorizacion, "autorizacion");
        this.asociaciones = Objects.requireNonNull(asociaciones, "asociaciones");
        this.cuentas = Objects.requireNonNull(cuentas, "cuentas");
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RolesCambiados asignar(CambioRolCommand comando) {
        autorizacion.exigirPermiso(comando.actor(), Permiso.ROL_ASIGNAR);
        Rol rol = Rol.desdeCodigo(comando.rol());
        CuentaRoles cuenta = bloquearYReautorizar(comando);

        boolean nuevo = asociaciones.asignar(cuenta.id(), rol);
        List<Rol> resultantes = rolesOrdenados(cuenta.id());
        if (nuevo) {
            aplicarEfectos(comando, RegistroRol.Accion.ROL_ASIGNADO, rol, resultantes);
        }
        return new RolesCambiados(cuenta, resultantes, nuevo);
    }

    /** Niveles 1 y 2 del orden de locks y reautorización bajo lock. */
    private CuentaRoles bloquearYReautorizar(CambioRolCommand comando) {
        autorizacion.bloquearCatalogoAdministrador();
        CuentaRoles cuenta = cuentas.bloquear(comando.usuarioObjetivo())
                .orElseThrow(() -> new UsuarioNoEncontradoException(comando.usuarioObjetivo()));
        autorizacion.exigirPermiso(comando.actor(), Permiso.ROL_ASIGNAR);
        return cuenta;
    }

    /** Nivel 3 y escrituras finales, todo en la transacción del cambio. */
    private void aplicarEfectos(CambioRolCommand comando, RegistroRol.Accion accion, Rol rol, List<Rol> resultantes) {
        UUID objetivo = comando.usuarioObjetivo();
        Instant ahora = clock.instant();
        sesiones.revocarTodas(objetivo);
        auditoria.registrarCritica(new RegistroRol(UUID.randomUUID(), accion, comando.actor().sub(), objetivo,
                rol, ahora, comando.contexto()));
        eventos.guardar(new RolesCambiadosEvento(UUID.randomUUID(), objetivo, resultantes, ahora));
    }

    /** Lista completa en el orden del catálogo, igual en la respuesta y en el evento. */
    private List<Rol> rolesOrdenados(UUID usuarioId) {
        return asociaciones.rolesDe(usuarioId).stream().sorted().toList();
    }
}
