package com.marketplace.auth_service.credencial.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.credencial.application.port.out.AuditoriaRecuperacionPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Registro no crítico de la solicitud; jamás almacena correo, HMAC ni token. */
public final class AuditoriaRecuperacionAdapter implements AuditoriaRecuperacionPort {
    private final AuditoriaPort auditoria;

    public AuditoriaRecuperacionAdapter(AuditoriaPort auditoria) {
        this.auditoria = Objects.requireNonNull(auditoria);
    }

    @Override
    public void registrar(UUID objetivoUsuarioId, boolean aceptada, ContextoPeticion contexto, Instant ahora) {
        auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "RECUPERACION_SOLICITADA",
                aceptada ? "EXITO" : "FALLO", "SISTEMA", null, objetivoUsuarioId, ahora,
                contexto.ip(), contexto.agente(), Map.of()));
    }
}
