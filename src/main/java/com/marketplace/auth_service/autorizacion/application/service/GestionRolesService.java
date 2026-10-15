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
import com.marketplace.auth_service.autorizacion.domain.exception.AdministradorProtegidoException;
import com.marketplace.auth_service.autorizacion.domain.exception.UsuarioNoEncontradoException;
import com.marketplace.auth_service.autorizacion.domain.model.CatalogoAutorizacion;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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
 *   <li>Nivel 2: lock de las cuentas de actor y objetivo (Eva), sin repetir y
 *       ordenadas por UUID como las ordena PostgreSQL. Si el objetivo no existe, 404.</li>
 *   <li>Reautoriza bajo lock: el permiso pudo retirarse mientras esperaba.</li>
 *   <li>Si el rol ya estaba asignado no hay cambio: responde con los roles
 *       actuales sin revocar sesiones, escribir, auditar ni publicar.</li>
 *   <li>Nivel 3: revoca todas las familias del objetivo (Jose Luis).</li>
 *   <li>Nivel 4: escribe la asociación propia ({@code usuario_rol}).</li>
 *   <li>Nivel 5: auditoría crítica y evento con la lista completa resultante, en el mismo commit (Christian).</li>
 * </ol>
 *
 * <p>Ningún paso vuelve a un nivel anterior.
 *
 * <p>Revocar sigue el mismo orden. Si el rol ya no estaba no hay cambio. Al
 * revocar {@code ADMIN_SISTEMA} aplica RF-11.7 tras la reautorización, con el
 * lock del catálogo tomado y antes de cualquier efecto: nadie se lo quita a sí
 * mismo y nunca queda el sistema sin un administrador activo. El lock serializa
 * estas revocaciones y los bloqueos o bajas, así que dos revocaciones cruzadas
 * no pueden dejar cero.
 */
public class GestionRolesService implements GestionRolesPort {

    /**
     * Orden de bloqueo de cuentas: el de PostgreSQL para {@code uuid} (bytes sin
     * signo), que coincide con el del texto en minúsculas. {@link UUID#compareTo}
     * compara con signo y daría otro orden.
     */
    static final Comparator<UUID> ORDEN_DE_BLOQUEO = Comparator.comparing(UUID::toString);

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
        Rol rol = CatalogoAutorizacion.rol(comando.rol());
        CuentaRoles cuenta = bloquearYReautorizar(comando);

        if (asociaciones.rolesDe(cuenta.id()).contains(rol)) {
            return new RolesCambiados(cuenta, rolesOrdenados(cuenta.id()), false);
        }
        sesiones.revocarTodas(cuenta.id());
        asociaciones.asignar(cuenta.id(), rol);
        List<Rol> resultantes = rolesOrdenados(cuenta.id());
        registrarCambio(comando, RegistroRol.Accion.ROL_ASIGNADO, rol, resultantes);
        return new RolesCambiados(cuenta, resultantes, true);
    }

    @Override
    public RolesCambiados revocar(CambioRolCommand comando) {
        autorizacion.exigirPermiso(comando.actor(), Permiso.ROL_ASIGNAR);
        Rol rol = CatalogoAutorizacion.rol(comando.rol());
        CuentaRoles cuenta = bloquearYReautorizar(comando);
        if (rol == Rol.ADMIN_SISTEMA) {
            protegerAdministrador(comando.actor().sub(), cuenta);
        }

        if (!asociaciones.rolesDe(cuenta.id()).contains(rol)) {
            return new RolesCambiados(cuenta, rolesOrdenados(cuenta.id()), false);
        }
        sesiones.revocarTodas(cuenta.id());
        asociaciones.quitar(cuenta.id(), rol);
        List<Rol> resultantes = rolesOrdenados(cuenta.id());
        registrarCambio(comando, RegistroRol.Accion.ROL_REVOCADO, rol, resultantes);
        return new RolesCambiados(cuenta, resultantes, true);
    }

    /**
     * RF-11.7. Cuenta los administradores con UUID de {@code usuario_rol} (Sergio)
     * y estado efectivo de las cuentas (Eva), sin leer la tabla {@code usuario}.
     * Primero el último administrador, para que ESC-11.5 (el único admin se quita
     * su propio rol) dé el mensaje de «único administrador».
     */
    private void protegerAdministrador(UUID actor, CuentaRoles objetivo) {
        boolean esAdministradorActivo = objetivo.activa()
                && asociaciones.rolesDe(objetivo.id()).contains(Rol.ADMIN_SISTEMA);
        if (esAdministradorActivo) {
            Set<UUID> otros = new HashSet<>(asociaciones.usuariosConRol(Rol.ADMIN_SISTEMA));
            otros.remove(objetivo.id());
            if (otros.isEmpty() || cuentas.contarActivos(otros) == 0) {
                throw new AdministradorProtegidoException(AdministradorProtegidoException.Motivo.ULTIMO_ADMINISTRADOR);
            }
        }
        if (actor.equals(objetivo.id())) {
            throw new AdministradorProtegidoException(AdministradorProtegidoException.Motivo.PROPIO);
        }
    }

    /** Niveles 1 y 2 del orden de locks y reautorización bajo lock. */
    private CuentaRoles bloquearYReautorizar(CambioRolCommand comando) {
        autorizacion.bloquearCatalogoAdministrador();
        UUID objetivo = comando.usuarioObjetivo();
        Map<UUID, CuentaRoles> bloqueadas = cuentas.bloquear(ordenDeBloqueo(comando.actor().sub(), objetivo));
        CuentaRoles cuenta = bloqueadas.get(objetivo);
        if (cuenta == null) {
            throw new UsuarioNoEncontradoException(objetivo);
        }
        autorizacion.exigirPermiso(comando.actor(), Permiso.ROL_ASIGNAR);
        return cuenta;
    }

    /** Cuentas a bloquear: sin repetir (autoasignación) y en {@link #ORDEN_DE_BLOQUEO}. */
    static List<UUID> ordenDeBloqueo(UUID... ids) {
        return Stream.of(ids).distinct().sorted(ORDEN_DE_BLOQUEO).toList();
    }

    /** Nivel 5: auditoría crítica y evento, en la transacción del cambio. */
    private void registrarCambio(CambioRolCommand comando, RegistroRol.Accion accion, Rol rol, List<Rol> resultantes) {
        UUID objetivo = comando.usuarioObjetivo();
        Instant ahora = clock.instant();
        auditoria.registrarCritica(new RegistroRol(UUID.randomUUID(), accion, comando.actor().sub(), objetivo,
                rol, ahora, comando.contexto()));
        eventos.guardar(new RolesCambiadosEvento(UUID.randomUUID(), objetivo, resultantes, ahora));
    }

    /** Lista completa en el orden del catálogo, igual en la respuesta y en el evento. */
    private List<Rol> rolesOrdenados(UUID usuarioId) {
        return asociaciones.rolesDe(usuarioId).stream().sorted().toList();
    }
}
