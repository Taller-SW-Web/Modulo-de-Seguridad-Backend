package com.marketplace.auth_service.credencial.application.dto;

/** Esquema público existente; sin lista común ni información de una cuenta. */
public record PoliticaPassword(int longitudMinima, boolean requiereMayuscula,
        boolean requiereMinuscula, boolean requiereDigito, boolean requiereCaracterEspecial,
        boolean rechazaComunes, boolean rechazaDatosPersonales, int historial,
        int caducidadDiasAdmin) {
    public static PoliticaPassword vigente() {
        return new PoliticaPassword(10, true, true, true, true, true, true, 5, 90);
    }
}
