package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Resultado del cierre (SPEC-06 RF-06.2/06.6): ambas variantes responden 204.
 * Solo {@code Cerrada} porta identidad, y solo ella habilitará el hecho
 * {@code SESION_CERRADA} cuando Christian entregue su puerto: un refresco
 * desconocido nunca audita una identidad inventada.
 */
public sealed interface CierreResultado permits CierreResultado.Cerrada, CierreResultado.YaCerrada {

    record Cerrada(UUID usuarioId, UUID familiaId) implements CierreResultado {
        public Cerrada {
            Objects.requireNonNull(usuarioId, "usuarioId");
            Objects.requireNonNull(familiaId, "familiaId");
        }
    }

    record YaCerrada() implements CierreResultado {
    }
}
