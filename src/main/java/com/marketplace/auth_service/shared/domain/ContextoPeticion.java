package com.marketplace.auth_service.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Datos de la petición que necesitan la auditoría y las sesiones
 * (ACUERDOS-INTERNOS §2, tipo común). Los arma el adaptador HTTP; nunca
 * contienen secretos, tokens ni el cuerpo de la petición.
 *
 * @param ip            dirección de origen tal como la resuelve el servidor
 * @param agente        cabecera {@code User-Agent}, recortada; puede ser {@code null}
 * @param correlacionId identificador para relacionar los registros de una misma petición
 */
public record ContextoPeticion(String ip, String agente, UUID correlacionId) {

    public static final int LARGO_MAXIMO_AGENTE = 255;

    public ContextoPeticion {
        Objects.requireNonNull(correlacionId, "correlacionId");
        if (agente != null && agente.length() > LARGO_MAXIMO_AGENTE) {
            agente = agente.substring(0, LARGO_MAXIMO_AGENTE);
        }
    }
}
