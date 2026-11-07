package com.marketplace.auth_service.direccion.adapter.out.persistence.adapter;

import com.marketplace.auth_service.direccion.adapter.out.persistence.entity.DireccionJpaEntity;
import com.marketplace.auth_service.direccion.adapter.out.persistence.repository.DireccionJpaRepository;
import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort.DatosNuevaDireccion;
import com.marketplace.auth_service.direccion.application.port.out.DireccionesPersistidasPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DireccionesPersistidasAdapter implements DireccionesPersistidasPort {
    private final DireccionJpaRepository repositorio;
    public DireccionesPersistidasAdapter(DireccionJpaRepository repositorio) { this.repositorio = repositorio; }

    @Override @Transactional(readOnly = true)
    public List<DireccionDatos> listar(UUID usuarioId) {
        return repositorio.findAllByUsuarioIdOrderByEsPredeterminadaDescIdAsc(usuarioId)
                .stream().map(DireccionesPersistidasAdapter::mapear).toList();
    }

    @Override @Transactional
    public DireccionDatos crear(UUID usuarioId, DatosNuevaDireccion datos) {
        List<DireccionJpaEntity> existentes = repositorio.findAllByUsuarioIdOrderByEsPredeterminadaDescIdAsc(usuarioId);
        boolean predeterminada = Boolean.TRUE.equals(datos.esPredeterminada()) || existentes.isEmpty();
        if (predeterminada && !existentes.isEmpty()) {
            existentes.forEach(d -> d.setEsPredeterminada(false));
            // Fuerza los UPDATE antes del INSERT para respetar el índice único parcial de PostgreSQL.
            repositorio.flush();
        }
        DireccionJpaEntity nueva = new DireccionJpaEntity(UUID.randomUUID(), usuarioId, opcional(datos.etiqueta()),
                limpiar(datos.departamento()), limpiar(datos.provincia()), limpiar(datos.distrito()),
                limpiar(datos.direccion()), opcional(datos.referencia()), predeterminada);
        return mapear(repositorio.saveAndFlush(nueva));
    }

    private static DireccionDatos mapear(DireccionJpaEntity d) {
        return new DireccionDatos(d.getId(), d.getEtiqueta(), d.getDepartamento(), d.getProvincia(), d.getDistrito(),
                d.getDireccion(), d.getReferencia(), d.isEsPredeterminada());
    }
    private static String limpiar(String valor) { return valor == null ? null : valor.trim(); }
    /** Campo opcional: vacío o solo espacios se guarda como null, no como cadena vacía. */
    private static String opcional(String valor) {
        String limpio = limpiar(valor);
        return limpio == null || limpio.isEmpty() ? null : limpio;
    }
}
