package com.marketplace.auth_service.auditoria.domain.exception;

/**
 * El código no pertenece al catálogo cerrado de SPEC-12 (RF-12.2, ESC-12.6).
 * El productor debe acordar el código con el PO en vez de inventarlo.
 */
public class AccionNoCatalogadaException extends RuntimeException {

    private final String codigo;

    public AccionNoCatalogadaException(String codigo) {
        super("La acción no pertenece al catálogo cerrado de auditoría: " + codigo);
        this.codigo = codigo;
    }

    /** El código recibido; {@code null} si no llegó ninguno. */
    public String codigo() {
        return codigo;
    }
}
