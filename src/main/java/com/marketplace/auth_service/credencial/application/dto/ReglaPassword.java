package com.marketplace.auth_service.credencial.application.dto;

/** Únicamente las reglas de PoliticaIncumplida del OpenAPI aprobado. */
public enum ReglaPassword {
    LONGITUD_MINIMA, MAYUSCULA, MINUSCULA, DIGITO, CARACTER_ESPECIAL,
    CONTRASENA_COMUN, DATOS_PERSONALES, YA_UTILIZADA
}
