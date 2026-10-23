package com.marketplace.auth_service.autorizacion.application.dto;

import java.util.List;
import java.util.Objects;

/** Rol del catálogo cerrado con sus permisos de este módulo (SPEC-18 RF-18.5, esquema {@code Rol}). */
public record RolCatalogado(String codigo, String nombre, String descripcion, List<String> permisos) {

    public RolCatalogado {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(nombre, "nombre");
        permisos = List.copyOf(permisos);
    }
}
