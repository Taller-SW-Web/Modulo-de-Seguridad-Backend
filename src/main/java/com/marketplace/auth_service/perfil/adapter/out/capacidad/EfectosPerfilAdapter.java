package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.perfil.application.port.out.EfectosPerfilPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Puente a AuditoriaPort y MensajeriaPort (Christian). El evento se guarda en el outbox dentro de
 * la transacción de la edición (si esta revierte, el evento desaparece); la auditoría no crítica
 * se intenta tras el commit. Solo viajan nombres de campos, jamás valores.
 */
public final class EfectosPerfilAdapter implements EfectosPerfilPort {
    public static final String TIPO_EVENTO = "usuario.atributos_actualizados";
    public static final String ACCION_AUDITORIA = "ATRIBUTOS_ACTUALIZADOS";

    private final AuditoriaPort auditoria;
    private final MensajeriaPort mensajeria;
    private final Clock reloj;

    public EfectosPerfilAdapter(AuditoriaPort auditoria, MensajeriaPort mensajeria, Clock reloj) {
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.mensajeria = Objects.requireNonNull(mensajeria, "mensajeria");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public void atributosActualizados(UUID actorId, UUID usuarioId, List<String> campos) {
        Instant ahora = reloj.instant();
        String lista = String.join(",", campos);
        mensajeria.guardarEvento(new EventoUsuario(UUID.randomUUID(), TIPO_EVENTO, Map.of(
                "usuarioId", usuarioId.toString(),
                "campos", lista,
                "fecha", ahora.toString())));
        auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), ACCION_AUDITORIA, "EXITO",
                "USUARIO", actorId.toString(), usuarioId, ahora, null, null, Map.of("campos", lista)));
    }
}
