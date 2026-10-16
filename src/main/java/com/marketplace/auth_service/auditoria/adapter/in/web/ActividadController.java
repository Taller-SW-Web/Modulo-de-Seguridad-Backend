package com.marketplace.auth_service.auditoria.adapter.in.web;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET {@code /api/v1/auth/me/actividad} (SPEC-13, EP-06-T004). El titular es siempre el
 * {@code sub} del acceso verificado: no hay parámetro para pedir la actividad de otra cuenta.
 */
@RestController
public class ActividadController {

    public record RegistroResponse(UUID id, Instant fecha, String accion, String resultado, String actorTipo,
                                   String actorId, UUID objetivoUsuarioId, String ip, String agente,
                                   Map<String, String> detalle) { }

    public record PaginaResponse(List<RegistroResponse> registros, int pagina, int tamano, long total) { }

    private final AuditoriaPort auditoria;

    public ActividadController(AuditoriaPort auditoria) {
        this.auditoria = auditoria;
    }

    @GetMapping(path = "/api/v1/auth/me/actividad", produces = MediaType.APPLICATION_JSON_VALUE)
    public PaginaResponse actividad(@AuthenticationPrincipal ActorUsuario actor,
                                    @RequestParam(name = "pagina", defaultValue = "1") int pagina,
                                    @RequestParam(name = "tamano", defaultValue = "20") int tamano) {
        int p = Math.max(1, pagina);
        int t = Math.min(100, Math.max(1, tamano));
        var resultado = auditoria.actividadPropia(actor.sub(), p, t);
        return new PaginaResponse(resultado.registros().stream().map(ActividadController::aRespuesta).toList(),
                p, t, resultado.total());
    }

    private static RegistroResponse aRespuesta(RegistroSeguro r) {
        return new RegistroResponse(r.registroId(), r.fecha(), r.accion(), r.resultado(), r.actorTipo(), r.actorId(),
                r.objetivoUsuarioId(), r.ip(), r.agente(), r.detalle().isEmpty() ? null : r.detalle());
    }
}
