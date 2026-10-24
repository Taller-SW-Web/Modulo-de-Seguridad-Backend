package com.marketplace.auth_service.proteccion.application.service;

import com.marketplace.auth_service.proteccion.application.dto.BloqueoVigente;
import com.marketplace.auth_service.proteccion.application.port.in.ConsultaBloqueosPort;
import com.marketplace.auth_service.proteccion.application.port.out.PersistenciaProteccionPort;
import com.marketplace.auth_service.proteccion.domain.model.Bloqueo;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Bloqueos vigentes para la vista administrativa (RF-15.6). Un bloqueo automático
 * vencido no se informa: la cuenta ya es ACTIVO por estado efectivo.
 */
public final class ConsultaBloqueosService implements ConsultaBloqueosPort {

    private final PersistenciaProteccionPort persistencia;
    private final Clock reloj;

    public ConsultaBloqueosService(PersistenciaProteccionPort persistencia, Clock reloj) {
        this.persistencia = Objects.requireNonNull(persistencia, "persistencia");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public Map<UUID, BloqueoVigente> vigentes(Set<UUID> usuarios) {
        Instant ahora = reloj.instant();
        Map<UUID, BloqueoVigente> resultado = new LinkedHashMap<>();
        for (UUID usuario : usuarios) {
            persistencia.buscarBloqueo(usuario)
                    .filter(b -> b.vigenteEn(ahora))
                    .ifPresent(b -> resultado.put(usuario, lectura(b)));
        }
        return resultado;
    }

    private static BloqueoVigente lectura(Bloqueo b) {
        return new BloqueoVigente(b.tipo().name(), b.bloqueadoHasta(), b.motivo());
    }
}
