package com.marketplace.auth_service.credencial.adapter.in.web;

import com.marketplace.auth_service.credencial.application.dto.ResultadoSolicitudRecuperacion;
import com.marketplace.auth_service.credencial.application.port.in.SolicitarRecuperacionPort;
import com.marketplace.auth_service.credencial.domain.exception.SolicitudRecuperacionNoDisponibleException;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint público SPEC-08 RF-08.1/RF-08.8; la respuesta nunca confirma existencia. */
@RestController
public class SolicitarRecuperacionController {
    private static final long LATENCIA_MINIMA_NANOS = TimeUnit.MILLISECONDS.toNanos(300);
    public record SolicitarRecuperacionRequest(String correo) {
        @Override public String toString() { return "SolicitarRecuperacionRequest[REDACTADO]"; }
    }

    private final SolicitarRecuperacionPort solicitudes;

    public SolicitarRecuperacionController(SolicitarRecuperacionPort solicitudes) {
        this.solicitudes = solicitudes;
    }

    @PostMapping(path = "/api/v1/password/recuperar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompletionStage<ResponseEntity<?>> solicitar(@RequestBody(required = false) SolicitarRecuperacionRequest cuerpo,
                                                        HttpServletRequest peticion) {
        if (!correoValido(cuerpo == null ? null : cuerpo.correo())) return validacion();
        long inicio = System.nanoTime();
        var resultado = solicitudes.solicitar(cuerpo.correo(), ContextoPeticiones.desde(peticion));
        ResponseEntity<?> respuesta = switch (resultado) {
            case ResultadoSolicitudRecuperacion.Aceptada aceptada -> ResponseEntity.accepted().build();
            case ResultadoSolicitudRecuperacion.Limite limite -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", Long.toString(limite.reintentarEnSegundos()))
                    .contentType(Problemas.PROBLEM_JSON)
                    .body(Problemas.problema(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADAS_SOLICITUDES",
                            "Demasiadas solicitudes", "Espera antes de pedir otro enlace."));
        };
        // La rama con cuenta hace más trabajo (token y outbox); un piso común reduce esa diferencia observable.
        long restante = LATENCIA_MINIMA_NANOS - (System.nanoTime() - inicio);
        if (restante <= 0) return CompletableFuture.completedFuture(respuesta);
        return CompletableFuture.supplyAsync(() -> respuesta,
                CompletableFuture.delayedExecutor(restante, TimeUnit.NANOSECONDS));
    }

    @ExceptionHandler({SolicitudRecuperacionNoDisponibleException.class, DataAccessException.class})
    ResponseEntity<ProblemDetail> noDisponible() {
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE",
                "Servicio no disponible", "Inténtalo nuevamente.");
    }

    private static boolean correoValido(String correo) {
        if (correo == null || correo.isBlank() || correo.length() > 254) return false;
        try {
            InternetAddress direccion = new InternetAddress(correo.strip(), true);
            direccion.validate();
            return direccion.getAddress().equals(correo.strip());
        } catch (AddressException invalido) {
            return false;
        }
    }

    private static CompletionStage<ResponseEntity<?>> validacion() {
        ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Datos de entrada inválidos", null);
        problema.setProperty("errores", List.of(Map.of("campo", "correo", "mensaje", "Escribe un correo válido.")));
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema));
    }
}
