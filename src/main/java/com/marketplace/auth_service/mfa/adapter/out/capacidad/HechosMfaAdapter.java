package com.marketplace.auth_service.mfa.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.mfa.application.port.out.HechosMfaPort;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Puente a la auditoría de Christian: no crítica, tras el commit de la solicitud. */
public final class HechosMfaAdapter implements HechosMfaPort {
    private final AuditoriaPort auditoria;

    public HechosMfaAdapter(AuditoriaPort auditoria) {
        this.auditoria = auditoria;
    }

    @Override
    public void otpSolicitado(UUID usuarioId) {
        auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "OTP_SOLICITADO", "EXITO", "USUARIO",
                usuarioId.toString(), usuarioId, Instant.now(), null, null, Map.of("canal", "EMAIL")));
    }
}
