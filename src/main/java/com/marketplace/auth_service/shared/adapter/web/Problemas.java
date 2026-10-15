package com.marketplace.auth_service.shared.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Locale;

/**
 * Respuestas de error del contrato (esquema {@code Problem}, RFC 7807): {@code type},
 * {@code title}, {@code status}, {@code code} y opcionalmente {@code detail}.
 *
 * <p>El {@code type} es el {@code code} en minúsculas con guiones bajo
 * {@code https://g7.unmsm.pe/errores/} (catálogo de errores). Los títulos son los
 * publicados en el contrato; cada dueño usa solo los códigos de su catálogo.
 */
public final class Problemas {

    public static final String BASE_TYPE = "https://g7.unmsm.pe/errores/";
    public static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    private Problemas() {
    }

    /** Construye el {@link ProblemDetail} con los campos obligatorios del contrato. */
    public static ProblemDetail problema(HttpStatus estado, String codigo, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatus(estado);
        problema.setType(URI.create(BASE_TYPE + codigo.toLowerCase(Locale.ROOT).replace('_', '-')));
        problema.setTitle(titulo);
        problema.setProperty("code", codigo);
        if (detalle != null) {
            problema.setDetail(detalle);
        }
        return problema;
    }

    /** La misma respuesta envuelta, con {@code application/problem+json}. */
    public static ResponseEntity<ProblemDetail> respuesta(HttpStatus estado, String codigo, String titulo,
                                                          String detalle) {
        return ResponseEntity.status(estado).contentType(PROBLEM_JSON)
                .body(problema(estado, codigo, titulo, detalle));
    }
}
