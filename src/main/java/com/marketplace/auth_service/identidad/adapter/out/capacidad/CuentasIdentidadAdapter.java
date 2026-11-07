package com.marketplace.auth_service.identidad.adapter.out.capacidad;

import com.marketplace.auth_service.identidad.application.dto.CuentaIdentidad;
import com.marketplace.auth_service.identidad.application.port.out.CuentasIdentidadPort;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Puente a CuentasPort (Eva): identidad, contacto y estado efectivo, sin documento ni contadores. */
public final class CuentasIdentidadAdapter implements CuentasIdentidadPort {

    private final CuentasPort cuentas;

    public CuentasIdentidadAdapter(CuentasPort cuentas) {
        this.cuentas = cuentas;
    }

    @Override
    public Optional<CuentaIdentidad> leer(UUID usuarioId) {
        return cuentas.leer(usuarioId).map(CuentasIdentidadAdapter::proyectar);
    }

    /** Sobrescribe el valor por defecto: dos consultas en total, sin N+1 (RF-18.2, NFR &lt;1s). */
    @Override
    public Map<UUID, CuentaIdentidad> leerLote(Set<UUID> usuarioIds) {
        Map<UUID, CuentaSnapshot> encontradas = cuentas.leerLote(usuarioIds);
        Map<UUID, CuentaIdentidad> proyectadas = new LinkedHashMap<>();
        encontradas.forEach((id, snapshot) -> proyectadas.put(id, proyectar(snapshot)));
        return proyectadas;
    }

    private static CuentaIdentidad proyectar(CuentaSnapshot s) {
        return new CuentaIdentidad(s.id(), s.nombres(), s.apellidos(), s.nombreCompleto(), s.correo(), s.celular(),
                s.estadoEfectivo(), s.correoVerificado(), s.celularVerificado());
    }
}
