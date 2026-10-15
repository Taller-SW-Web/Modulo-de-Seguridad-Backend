package com.marketplace.auth_service.identidad.adapter.in.web;

import com.marketplace.auth_service.identidad.adapter.in.web.dto.DescubrimientoResponse;
import com.marketplace.auth_service.identidad.adapter.in.web.dto.JwksResponse;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Publicación de la parte pública de la firma (TEC-IDENTIDAD-T003, SPEC-17
 * RF-17.1/17.2, ESC-17.1). Ambas rutas son públicas y no tocan la base de datos.
 *
 * <ul>
 *   <li>{@code GET /api/v1/auth/.well-known/jwks.json}: claves de verificación.</li>
 *   <li>{@code GET /api/v1/auth/.well-known/openid-configuration}: issuer y jwks_uri,
 *       para que un consumidor se configure con una sola URL ({@code issuer-uri}).</li>
 * </ul>
 *
 * <p>Se pueden cachear una hora: durante una rotación la clave anterior sigue
 * publicada al menos 24 h (RF-17.3), así que una copia de una hora nunca deja
 * de servir. Si la firma no está configurada responden {@code 503 NO_DISPONIBLE}.
 */
@RestController
@RequestMapping("/api/v1/auth/.well-known")
public class DescubrimientoController {

    static final CacheControl CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private static final Logger log = LoggerFactory.getLogger(DescubrimientoController.class);

    private final FirmaPort firmaPort;

    public DescubrimientoController(FirmaPort firmaPort) {
        this.firmaPort = firmaPort;
    }

    @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JwksResponse> jwks() {
        return ResponseEntity.ok().cacheControl(CACHE).body(IdentidadWebMapper.aJwks(firmaPort.jwks()));
    }

    @GetMapping(value = "/openid-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DescubrimientoResponse> descubrimiento() {
        return ResponseEntity.ok().cacheControl(CACHE)
                .body(IdentidadWebMapper.aDescubrimiento(firmaPort.descubrimiento()));
    }

    /** El motivo concreto queda en el log del servidor; al consumidor solo se le indica usar su caché. */
    @ExceptionHandler(FirmaNoDisponibleException.class)
    ResponseEntity<ProblemDetail> noDisponible(FirmaNoDisponibleException e) {
        log.warn("identidad: descubrimiento/JWKS no disponible: {}", e.getMessage());
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE", "Servicio no disponible",
                "Usa tu copia en caché del JWKS.");
    }
}
