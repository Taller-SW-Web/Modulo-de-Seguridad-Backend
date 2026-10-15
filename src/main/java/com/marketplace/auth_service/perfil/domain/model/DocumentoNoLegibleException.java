package com.marketplace.auth_service.perfil.domain.model;

/**
 * El documento cifrado no se pudo leer (clave ausente, etiqueta o formato
 * inválidos). No se oculta devolviendo null: quien proyecta debe tratarlo como
 * un fallo de integridad.
 */
public class DocumentoNoLegibleException extends RuntimeException {
    public DocumentoNoLegibleException(String mensaje) {
        super(mensaje);
    }

    public DocumentoNoLegibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
