package com.marketplace.auth_service.perfil.application.dto;

import java.util.Objects;

/** Cambio propio permitido en H3: solo nombres, apellidos y celular. Ningún otro campo existe aquí. */
public record CambioAtributos(CampoEditable<String> nombres, CampoEditable<String> apellidos, CampoEditable<String> celular) {
    public CambioAtributos {
        Objects.requireNonNull(nombres, "nombres");
        Objects.requireNonNull(apellidos, "apellidos");
        Objects.requireNonNull(celular, "celular");
    }

    public boolean hayCambios() {
        return nombres.presente() || apellidos.presente() || celular.presente();
    }
}
