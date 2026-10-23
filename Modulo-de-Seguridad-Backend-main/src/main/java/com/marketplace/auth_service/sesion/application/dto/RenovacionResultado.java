package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Resultado de negocio de renovar (SPEC-06 RF-06.1/06.3/06.4): el reúso se
 * devuelve como resultado, no como excepción, para que la revocación de la
 * familia confirme antes del 401 (ACUERDOS-INTERNOS §3). Los identificadores
 * del rechazo permiten auditar {@code REFRESCO_REUTILIZADO} cuando Christian
 * entregue su puerto; nunca viajan al HTTP.
 */
public sealed interface RenovacionResultado permits RenovacionResultado.Renovada,
        RenovacionResultado.Rechazada {

    record Renovada(SesionIniciada sesion) implements RenovacionResultado {
        public Renovada {
            Objects.requireNonNull(sesion, "sesion");
        }
    }

    record Rechazada(MotivoRechazo motivo, UUID usuarioId, UUID familiaId) implements RenovacionResultado {
        public Rechazada {
            Objects.requireNonNull(motivo, "motivo");
        }

        /** Solo el reúso revoca y exige auditoría; el resto no muta nada. */
        public boolean revocoFamilia() {
            return motivo == MotivoRechazo.REUSO_DETECTADO;
        }
    }
}
