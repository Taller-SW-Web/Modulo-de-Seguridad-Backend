package com.marketplace.auth_service.sesion.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.sesion.application.port.out.EfectosCierrePort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Puente local de cierre → auditoría (FEAT-12-T2): cada capacidad se consume
 * por su puerto exportado (ACUERDOS-INTERNOS §2). {@code SESION_CERRADA} no es
 * crítica en el catálogo (SPEC-12): se registra tras el commit con
 * deduplicación por {@code registroId}, y su fallo deja el logout en 204.
 *
 * <p>El detalle solo referencia la familia cerrada por su id: sin tokens,
 * refrescos ni datos de la petición além de ip y agente (RF-12.5).
 */
public final class EfectosCierreAdapter implements EfectosCierrePort {

    private final AuditoriaPort auditoria;

    public EfectosCierreAdapter(AuditoriaPort auditoria) {
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
    }

    @Override
    public void auditarCierre(UUID usuarioId, UUID familiaId, ContextoPeticion contexto, Instant ahora) {
        auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "SESION_CERRADA", "EXITO",
                "USUARIO", usuarioId.toString(), usuarioId, ahora, contexto.ip(), contexto.agente(),
                Map.of("familiaId", familiaId.toString())));
    }
}
