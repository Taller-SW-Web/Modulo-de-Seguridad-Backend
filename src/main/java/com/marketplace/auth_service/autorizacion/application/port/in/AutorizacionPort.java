package com.marketplace.auth_service.autorizacion.application.port.in;

import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;

import java.util.Set;
import java.util.UUID;

/**
 * Capacidad exportada de autorización (ACUERDOS-INTERNOS §2). Los demás
 * dueños la consumen solo por este puerto, nunca por los repositorios.
 *
 * <p>Regla de oro: <b>los permisos del token no autorizan</b>. Toda decisión
 * protegida se toma con los permisos actuales de la base, porque pudieron
 * cambiar después de emitirse el acceso. Ocultar un botón en la interfaz o
 * tener un scope de servicio tampoco concede un permiso de persona.
 *
 * <p>Solo expone tipos exportables: DTO de {@code application.dto} y
 * vocabulario común de {@code shared.domain}. Un consumidor lo usa desde un
 * puente en su {@code adapter.out.capacidad}.
 *
 * <p>EP-05-T001 entrega {@code resolver}. EP-05-T002 añade
 * {@code exigirPermiso}, {@code exigirTitularOPrivilegio},
 * {@code bloquearCatalogoAdministrador} y {@code usuariosConRol}.
 */
public interface AutorizacionPort {

    /**
     * Roles y permisos actuales de una persona, leídos de la base en cada
     * llamada (RF-11.4). El login los pone en el acceso.
     *
     * @return listas vacías si la persona no tiene roles; nunca null
     */
    PermisosResueltos resolver(UUID usuarioId);

    /**
     * Exige que el actor tenga hoy {@code permiso}, revalidado contra la base
     * (no contra los claims de su token).
     *
     * @throws AccesoDenegadoException si no lo tiene → {@code 403 SCOPE_INSUFICIENTE}
     */
    void exigirPermiso(ActorUsuario actor, Permiso permiso);

    /**
     * Permite la operación si recae sobre la propia cuenta del actor
     * (titularidad, comparando {@code sub}) o si el actor tiene {@code permiso}.
     *
     * <p>RF-11.10: la titularidad solo cubre lo que la spec permite al titular
     * (ver su perfil, cambiar su contraseña, editar nombres/apellidos/celular).
     * Para documento y atributos de rol se usa {@link #exigirPermiso} con
     * {@code atributos.editar} <b>incluso sobre la cuenta propia</b>.
     *
     * @throws AccesoDenegadoException si no es titular ni tiene el permiso
     */
    void exigirTitularOPrivilegio(ActorUsuario actor, UUID usuarioObjetivo, Permiso permiso);

    /**
     * Bloquea la fila fija {@code rol.ADMIN_SISTEMA} hasta el fin de la
     * transacción en curso (nivel 1 del orden de locks, ACUERDOS-INTERNOS §3).
     *
     * <p>Serializa toda operación que pueda dejar al sistema sin administrador
     * activo: asignar/revocar roles, registrar un fallo de login, desbloquear y
     * las futuras baja/reactivación/bloqueo manual. Se llama <b>antes</b> de
     * bloquear cuentas o familias. Exige una transacción ya abierta.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException sin transacción
     */
    void bloquearCatalogoAdministrador();

    /**
     * UUID de las personas que tienen {@code rol}. Lo consume Eva para filtrar
     * el listado por rol y para contar administradores activos con su estado
     * efectivo, sin consultar {@code usuario_rol} desde su repositorio.
     */
    Set<UUID> usuariosConRol(Rol rol);
}
