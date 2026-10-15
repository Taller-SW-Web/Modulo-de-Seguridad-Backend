package com.marketplace.auth_service.credencial.adapter.in.web;

import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import java.util.LinkedHashMap;

/** Compartible con el futuro endpoint de cambio, sin implementarlo en este bloque. */
public final class PoliticaPasswordWebMapper {
    private PoliticaPasswordWebMapper() { }
    public static PoliticaPasswordResponse aRespuesta(PoliticaPassword p) {
        return new PoliticaPasswordResponse(p.longitudMinima(), p.requiereMayuscula(), p.requiereMinuscula(),
                p.requiereDigito(), p.requiereCaracterEspecial(), p.rechazaComunes(), p.rechazaDatosPersonales(),
                p.historial(), p.caducidadDiasAdmin());
    }
    public static ProblemDetail aProblema(ValidacionPassword resultado) {
        if (resultado.valida()) throw new IllegalArgumentException("No hay infracciones");
        var problema = Problemas.problema(HttpStatus.UNPROCESSABLE_ENTITY, "POLITICA_INCUMPLIDA",
                "La contraseña no cumple la política", null);
        problema.setProperty("errores", resultado.errores().stream().map(error -> {
            var campos = new LinkedHashMap<String, Object>();
            campos.put("regla", error.regla().name());
            if (error.esperado() != null) campos.put("esperado", error.esperado());
            return campos;
        }).toList());
        return problema;
    }
}
