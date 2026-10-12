package com.marketplace.auth_service.identidad.adapter.in.web;

import com.marketplace.auth_service.identidad.adapter.in.web.dto.DescubrimientoResponse;
import com.marketplace.auth_service.identidad.adapter.in.web.dto.JwksResponse;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.Descubrimiento;

import java.util.List;

/** Resultados de aplicación → respuestas HTTP del contrato. Sin lógica ni consultas. */
final class IdentidadWebMapper {

    private IdentidadWebMapper() {
    }

    static JwksResponse aJwks(List<ClavePublica> claves) {
        return new JwksResponse(claves.stream()
                .map(c -> new JwksResponse.Jwk("RSA", ClavePublica.USO, ClavePublica.ALGORITMO, c.kid(), c.n(), c.e()))
                .toList());
    }

    static DescubrimientoResponse aDescubrimiento(Descubrimiento d) {
        return new DescubrimientoResponse(d.issuer(), d.jwksUri(), d.algoritmosFirma());
    }
}
