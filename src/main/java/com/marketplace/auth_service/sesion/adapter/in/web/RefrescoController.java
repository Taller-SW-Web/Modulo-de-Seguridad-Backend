package com.marketplace.auth_service.sesion.adapter.in.web;

import com.marketplace.auth_service.sesion.adapter.in.web.dto.RenovarSesionRequest;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST {@code /api/v1/auth/refresh} (SPEC-06 RF-06.1/06.3/06.4).
 *
 * <p>Es público: se autoriza con el refresco del cuerpo. Válido rota y devuelve
 * el nuevo par; desconocido/vencido/revocado/reutilizado responde el mismo
 * {@code 401 REFRESCO_INVALIDO}, sin revelar la causa. El reúso revoca su
 * familia y confirma antes del 401; las otras familias siguen vigentes.
 * Ningún secreto sale a logs: solo jti/familia para auditar el reúso.
 */
@RestController
public class RefrescoController {

    private static final Logger log = LoggerFactory.getLogger(RefrescoController.class);

    private final SesionesPort sesiones;

    public RefrescoController(SesionesPort sesiones) {
        this.sesiones = sesiones;
    }

    @PostMapping(path = "/api/v1/auth/refresh", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> renovar(@RequestBody(required = false) RenovarSesionRequest cuerpo) {
        RenovacionResultado resultado = sesiones.renovar(SesionWebMapper.aSecreto(cuerpo));
        return switch (resultado) {
            case RenovacionResultado.Renovada renovada ->
                    ResponseEntity.ok(SesionWebMapper.aRespuesta(renovada.sesion()));
            case RenovacionResultado.Rechazada rechazada -> {
                if (rechazada.revocoFamilia()) {
                    log.warn("refresco reutilizado: usuario={} familia={}", rechazada.usuarioId(),
                            rechazada.familiaId());
                }
                yield refrescoInvalido();
            }
        };
    }

    static ResponseEntity<ProblemDetail> refrescoInvalido() {
        return Problemas.respuesta(HttpStatus.UNAUTHORIZED, "REFRESCO_INVALIDO",
                "La sesión no es válida", "Vuelve a iniciar sesión.");
    }
}
