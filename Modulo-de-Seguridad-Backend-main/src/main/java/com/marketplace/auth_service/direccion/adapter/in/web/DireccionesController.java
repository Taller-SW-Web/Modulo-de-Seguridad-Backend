package com.marketplace.auth_service.direccion.adapter.in.web;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort.DatosNuevaDireccion;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios/{id}/direcciones")
public class DireccionesController {
    private final DireccionesPort direcciones;
    public DireccionesController(DireccionesPort direcciones) { this.direcciones = direcciones; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> listar(@AuthenticationPrincipal ActorUsuario actor, @PathVariable UUID id) {
        List<DireccionResponse> resultado = direcciones.listar(actor, id).stream()
                .map(DireccionesController::respuesta).toList();
        return Map.of("direcciones", resultado);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DireccionResponse> crear(@AuthenticationPrincipal ActorUsuario actor,
            @PathVariable UUID id, @Valid @RequestBody DireccionRequest request) {
        DireccionDatos creada = direcciones.crear(actor, id, new DatosNuevaDireccion(request.etiqueta(),
                request.departamento(), request.provincia(), request.distrito(), request.direccion(),
                request.referencia(), request.esPredeterminada()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta(creada));
    }

    private static DireccionResponse respuesta(DireccionDatos d) {
        return new DireccionResponse(d.id(), d.etiqueta(), d.departamento(), d.provincia(), d.distrito(),
                d.direccion(), d.referencia(), d.esPredeterminada());
    }
}
