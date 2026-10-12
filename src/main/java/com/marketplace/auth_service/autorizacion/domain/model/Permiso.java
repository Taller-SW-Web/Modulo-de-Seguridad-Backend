package com.marketplace.auth_service.autorizacion.domain.model;

import java.util.Optional;

/**
 * Catálogo de permisos de este módulo (SPEC-11, tabla «Catálogo de permisos»).
 * Se escriben {@code recurso.accion}, con punto: los dos puntos son de los
 * scopes de servicio, así un permiso nunca se confunde con un scope.
 *
 * <p>Solo existen los de Seguridad: los demás módulos autorizan por rol y con
 * sus propios datos (RF-11.9, acuerdo A5).
 */
public enum Permiso {

    USUARIO_VER("usuario.ver"),
    USUARIO_CREAR("usuario.crear"),
    USUARIO_DESACTIVAR("usuario.desactivar"),
    USUARIO_REACTIVAR("usuario.reactivar"),
    ROL_ASIGNAR("rol.asignar"),
    AUDITORIA_VER("auditoria.ver"),
    CUENTA_BLOQUEAR("cuenta.bloquear"),
    ATRIBUTOS_EDITAR("atributos.editar"),
    CLIENTE_REGISTRAR("cliente.registrar"),
    CLIENTE_BUSCAR("cliente.buscar");

    private final String codigo;

    Permiso(String codigo) {
        this.codigo = codigo;
    }

    /** El código tal como viaja en el claim {@code permisos} y se guarda en la tabla {@code permiso}. */
    public String codigo() {
        return codigo;
    }

    public static Optional<Permiso> desdeCodigo(String codigo) {
        for (Permiso permiso : values()) {
            if (permiso.codigo.equals(codigo)) {
                return Optional.of(permiso);
            }
        }
        return Optional.empty();
    }
}
