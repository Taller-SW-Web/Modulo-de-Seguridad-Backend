package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.usuario.application.dto.CampoBasico;
import java.util.Objects;

/** Solo columnas de cuenta; documento y atributos de perfil pertenecen a Valery. */
public record CambioBasicos(CampoBasico<String> nombres, CampoBasico<String> apellidos, CampoBasico<String> celular) {
    public CambioBasicos {
        Objects.requireNonNull(nombres, "nombres");
        Objects.requireNonNull(apellidos, "apellidos");
        Objects.requireNonNull(celular, "celular");
        if ((nombres.presente() && nombres.valor() == null) || (apellidos.presente() && apellidos.valor() == null))
            throw new IllegalArgumentException("Nombres y apellidos no admiten null");
    }
}
