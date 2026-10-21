package com.marketplace.auth_service.autorizacion.application.service;

import com.marketplace.auth_service.autorizacion.application.dto.PermisoCatalogado;
import com.marketplace.auth_service.autorizacion.application.dto.RolCatalogado;
import com.marketplace.auth_service.autorizacion.application.port.in.CatalogoPort;
import com.marketplace.auth_service.autorizacion.application.port.out.CatalogoLecturaPort;
import com.marketplace.auth_service.shared.domain.Rol;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Catálogo para consumidores (SPEC-18 ESC-18.7). Ordena los roles como el
 * catálogo cerrado (CLIENTE … ADMIN_SISTEMA) y los permisos por código, para
 * que la respuesta sea estable entre llamadas.
 */
public final class CatalogoService implements CatalogoPort {

    private final CatalogoLecturaPort lectura;

    public CatalogoService(CatalogoLecturaPort lectura) {
        this.lectura = Objects.requireNonNull(lectura, "lectura");
    }

    @Override
    public List<RolCatalogado> roles() {
        return lectura.roles().stream()
                .sorted(Comparator.comparingInt(r -> Rol.buscar(r.codigo()).map(Enum::ordinal).orElse(Integer.MAX_VALUE)))
                .map(r -> new RolCatalogado(r.codigo(), r.nombre(), r.descripcion(),
                        r.permisos().stream().sorted().toList()))
                .toList();
    }

    @Override
    public List<PermisoCatalogado> permisos(String modulo) {
        boolean todos = modulo == null || modulo.isBlank();
        return lectura.permisos().stream()
                .filter(p -> todos || p.modulo().equals(modulo.strip()))
                .sorted(Comparator.comparing(PermisoCatalogado::codigo))
                .toList();
    }
}
