package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.proteccion.application.port.in.ConsultaBloqueosPort;
import com.marketplace.auth_service.usuario.application.dto.BloqueoVista;
import com.marketplace.auth_service.usuario.application.port.out.BloqueosConsultaPort;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Puente hacia la lectura exportada de protección; sin repositorios ni SQL ajenos. */
public final class BloqueosConsultaAdapter implements BloqueosConsultaPort {
    private final ConsultaBloqueosPort proteccion;

    public BloqueosConsultaAdapter(ConsultaBloqueosPort proteccion) {
        this.proteccion = Objects.requireNonNull(proteccion, "proteccion");
    }

    @Override
    public Map<UUID, BloqueoVista> vigentes(Set<UUID> usuarios) {
        Map<UUID, BloqueoVista> vistas = new LinkedHashMap<>();
        proteccion.vigentes(usuarios).forEach((id, b) -> vistas.put(id, new BloqueoVista(b.tipo(), b.hasta(), b.motivo())));
        return vistas;
    }
}
