package com.marketplace.auth_service.identidad.adapter.in.transaction;

import com.marketplace.auth_service.identidad.application.dto.ResultadoConsultaIdentidad;
import com.marketplace.auth_service.identidad.application.port.in.ConsultaIdentidadPort;
import com.marketplace.auth_service.shared.domain.ActorServicio;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frontera transaccional de {@link ConsultaIdentidadPort} (SPEC-18 FEAT-06-T1).
 * No es de solo lectura: cuando el documento sale en claro, la auditoría
 * crítica escribe dentro de esta misma transacción (RF-12.6); la no crítica
 * se encola para después del commit.
 */
public class ConsultaIdentidadTransaccional implements ConsultaIdentidadPort {

    private final ConsultaIdentidadPort casoDeUso;

    public ConsultaIdentidadTransaccional(ConsultaIdentidadPort casoDeUso) {
        this.casoDeUso = casoDeUso;
    }

    @Override
    @Transactional
    public ResultadoConsultaIdentidad obtener(ActorServicio actor, String idTexto) {
        return casoDeUso.obtener(actor, idTexto);
    }
}
