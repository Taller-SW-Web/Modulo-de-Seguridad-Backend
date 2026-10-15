package com.marketplace.auth_service.sesion.adapter.in.web;

import com.marketplace.auth_service.sesion.adapter.in.web.dto.RenovarSesionRequest;
import com.marketplace.auth_service.sesion.application.dto.CierreResultado;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST {@code /api/v1/auth/logout} (SPEC-06 RF-06.2/06.6, ESC-06.3/06.5).
 *
 * <p>Es público: se autoriza con el refresco del cuerpo, sin exigir Bearer ni
 * acceso vigente. Cierra esa familia y responde 204 también con refresco
 * desconocido, vencido o ya revocado: idempotente y sin revelar nada. No
 * cierra otras familias del usuario. El hecho {@code SESION_CERRADA} se
 * registrará con Christian solo desde {@code Cerrada}, nunca con una
 * identidad inventada.
 */
@RestController
public class CierreController {

    private static final Logger log = LoggerFactory.getLogger(CierreController.class);

    private final SesionesPort sesiones;

    public CierreController(SesionesPort sesiones) {
        this.sesiones = sesiones;
    }

    @PostMapping(path = "/api/v1/auth/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> cerrar(@RequestBody(required = false) RenovarSesionRequest cuerpo) {
        CierreResultado resultado = sesiones.cerrar(SesionWebMapper.aSecreto(cuerpo));
        if (resultado instanceof CierreResultado.Cerrada cerrada) {
            log.info("sesión cerrada: usuario={} familia={}", cerrada.usuarioId(), cerrada.familiaId());
        }
        return ResponseEntity.noContent().build();
    }
}
