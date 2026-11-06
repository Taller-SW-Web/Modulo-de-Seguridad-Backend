package com.marketplace.auth_service.usuario.domain.exception;

/** Más de 100 identificadores en {@code POST /usuarios/lote} (SPEC-18 ESC-18.3): 400 {@code LOTE_DEMASIADO_GRANDE}. */
public class LoteDemasiadoGrandeException extends RuntimeException {

    private final int recibidos;
    private final int maximo;

    public LoteDemasiadoGrandeException(int recibidos, int maximo) {
        super("Se recibieron " + recibidos + " identificadores; el máximo es " + maximo + ".");
        this.recibidos = recibidos;
        this.maximo = maximo;
    }

    public int recibidos() {
        return recibidos;
    }

    public int maximo() {
        return maximo;
    }
}
