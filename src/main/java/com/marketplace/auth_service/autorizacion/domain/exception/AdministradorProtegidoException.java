package com.marketplace.auth_service.autorizacion.domain.exception;

/**
 * RF-11.7: un {@code ADMIN_SISTEMA} no se quita a sí mismo ese rol, y nadie se
 * lo quita al último administrador activo. El adaptador HTTP responde
 * {@code 422 ADMINISTRADOR_PROTEGIDO}, el mismo código que usan la baja
 * (SPEC-04) y el bloqueo (SPEC-15), porque es una sola regla.
 */
public class AdministradorProtegidoException extends RuntimeException {

    /** Qué parte de la regla se aplicó; cambia solo el detalle del mensaje. */
    public enum Motivo {
        /** Quedaría el sistema sin ningún {@code ADMIN_SISTEMA} activo (ESC-11.5). */
        ULTIMO_ADMINISTRADOR,
        /** El actor intenta quitarse su propio {@code ADMIN_SISTEMA}. */
        PROPIO
    }

    private final Motivo motivo;

    public AdministradorProtegidoException(Motivo motivo) {
        super("Operación no permitida sobre este administrador: " + motivo);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
