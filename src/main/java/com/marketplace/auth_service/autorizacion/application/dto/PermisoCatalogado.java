package com.marketplace.auth_service.autorizacion.application.dto;

import java.util.Objects;

/** Permiso del catálogo (SPEC-18 RF-18.5, esquema {@code Permiso}). Hoy solo los de Seguridad. */
public record PermisoCatalogado(String codigo, String modulo, String descripcion) {

    public PermisoCatalogado {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(modulo, "modulo");
    }
}
