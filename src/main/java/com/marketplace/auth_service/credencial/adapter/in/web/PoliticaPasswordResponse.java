package com.marketplace.auth_service.credencial.adapter.in.web;

public record PoliticaPasswordResponse(int longitudMinima, boolean requiereMayuscula,
        boolean requiereMinuscula, boolean requiereDigito, boolean requiereCaracterEspecial,
        boolean rechazaComunes, boolean rechazaDatosPersonales, int historial, int caducidadDiasAdmin) { }
