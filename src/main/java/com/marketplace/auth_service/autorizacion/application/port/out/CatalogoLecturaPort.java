package com.marketplace.auth_service.autorizacion.application.port.out;

import com.marketplace.auth_service.autorizacion.application.dto.PermisoCatalogado;
import com.marketplace.auth_service.autorizacion.application.dto.RolCatalogado;

import java.util.List;

/** Lectura del catálogo sembrado ({@code rol}, {@code permiso}, {@code rol_permiso}). Sin escritura. */
public interface CatalogoLecturaPort {

    List<RolCatalogado> roles();

    List<PermisoCatalogado> permisos();
}
