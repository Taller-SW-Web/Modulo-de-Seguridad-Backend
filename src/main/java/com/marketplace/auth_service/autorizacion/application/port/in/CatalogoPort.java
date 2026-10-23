package com.marketplace.auth_service.autorizacion.application.port.in;

import com.marketplace.auth_service.autorizacion.application.dto.PermisoCatalogado;
import com.marketplace.auth_service.autorizacion.application.dto.RolCatalogado;

import java.util.List;

/**
 * Catálogo de roles y permisos para los módulos consumidores (SPEC-18 RF-18.5,
 * ESC-18.7): lo leen en vez de codificarlo a mano. Se lee de la base sembrada,
 * así un permiso nuevo llega sin desplegar a los consumidores.
 */
public interface CatalogoPort {

    /** Los seis roles en su orden canónico, cada uno con sus permisos ordenados. */
    List<RolCatalogado> roles();

    /** Permisos ordenados por código; {@code modulo} null o vacío = todos. */
    List<PermisoCatalogado> permisos(String modulo);
}
