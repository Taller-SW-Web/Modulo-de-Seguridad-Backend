package com.marketplace.auth_service.shared.domain;

import java.util.Optional;

/**
 * Catálogo cerrado de los seis roles del sistema (SPEC-11 RF-11.1). Los
 * nombres coinciden con el enum {@code codigo_rol} de PostgreSQL y con el
 * contrato; no hay códigos en inglés (ADR-004) ni roles de otros módulos.
 *
 * <p>Es vocabulario común, como {@link ActorUsuario}: los acuerdos lo usan en
 * puertos de varios dueños ({@code exigirPermiso}, {@code usuariosConRol},
 * {@code MfaPort.esEfectivo}, {@code CredencialesPort.evaluarCaducidad}).
 * La matriz rol → permisos y la asignación siguen siendo de autorización.
 *
 * <p>La etiqueta es la canónica de FRONT-00, la misma que muestra la interfaz.
 */
public enum Rol {

    CLIENTE("Cliente", false),
    VENDEDOR("Vendedor", false),
    ADMIN_VENTAS("Administrador de ventas", true),
    GESTOR_DESPACHO("Gestor de despacho", true),
    GESTOR_COMERCIAL("Gestor comercial", true),
    ADMIN_SISTEMA("Administrador del sistema", true);

    private final String etiqueta;
    private final boolean deGestion;

    Rol(String etiqueta, boolean deGestion) {
        this.etiqueta = etiqueta;
        this.deGestion = deGestion;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /**
     * Rol de gestión según el glosario (CONTEXT.md): personal de la plataforma.
     * Tener uno solo basta para que el segundo factor sea obligatorio (RF-10.1).
     */
    public boolean esDeGestion() {
        return deGestion;
    }

    /**
     * Busca un código externo, distinguiendo mayúsculas: {@code "admin_sistema"},
     * {@code "BUYER"} o {@code "SUPER_ADMIN"} no son roles. Quien necesite un
     * error de validación lo decide (autorización usa {@code CatalogoAutorizacion.rol}).
     */
    public static Optional<Rol> buscar(String codigo) {
        if (codigo != null) {
            for (Rol rol : values()) {
                if (rol.name().equals(codigo)) {
                    return Optional.of(rol);
                }
            }
        }
        return Optional.empty();
    }
}
