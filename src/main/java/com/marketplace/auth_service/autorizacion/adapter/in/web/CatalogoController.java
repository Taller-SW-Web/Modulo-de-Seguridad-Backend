package com.marketplace.auth_service.autorizacion.adapter.in.web;

import com.marketplace.auth_service.autorizacion.application.dto.PermisoCatalogado;
import com.marketplace.auth_service.autorizacion.application.dto.RolCatalogado;
import com.marketplace.auth_service.autorizacion.application.port.in.CatalogoPort;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Catálogo de roles y permisos para módulos consumidores (SPEC-18 ESC-18.7).
 * Ambas rutas exigen token de servicio con {@code roles:leer}; la regla de la
 * ruta lo comprobó antes de llegar aquí. Cacheable 5 minutos: el catálogo solo
 * cambia por migración.
 */
@RestController
public class CatalogoController {

    static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate();

    private final CatalogoPort catalogo;

    public CatalogoController(CatalogoPort catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping(path = "/api/v1/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RolesResponse> roles() {
        return ResponseEntity.ok().cacheControl(CACHE).body(new RolesResponse(catalogo.roles()));
    }

    @GetMapping(path = "/api/v1/permisos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PermisosResponse> permisos(@RequestParam(name = "modulo", required = false) String modulo) {
        return ResponseEntity.ok().cacheControl(CACHE).body(new PermisosResponse(catalogo.permisos(modulo)));
    }

    /** Cuerpo de {@code GET /roles}: los campos de {@code RolCatalogado} coinciden con el esquema {@code Rol}. */
    public record RolesResponse(List<RolCatalogado> roles) { }

    /** Cuerpo de {@code GET /permisos}: los campos de {@code PermisoCatalogado} coinciden con {@code Permiso}. */
    public record PermisosResponse(List<PermisoCatalogado> permisos) { }
}
