package com.marketplace.auth_service.credencial.adapter.out.persistence;

import com.marketplace.auth_service.credencial.adapter.out.persistence.mapper.*;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.*;
import com.marketplace.auth_service.credencial.application.dto.RevisionCredencial;
import com.marketplace.auth_service.credencial.application.port.out.PersistenciaCredencialesPort;
import com.marketplace.auth_service.credencial.domain.model.*;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CredencialesJpaAdapter implements PersistenciaCredencialesPort {
    private final CredencialJpaRepository credenciales;
    private final PasswordHistorialJpaRepository historiales;
    public CredencialesJpaAdapter(CredencialJpaRepository credenciales, PasswordHistorialJpaRepository historiales) {
        this.credenciales = credenciales;
        this.historiales = historiales;
    }
    @Override public Optional<Credencial> leer(UUID usuarioId) {
        // Proyección escalar: no devuelve la entidad que una lectura anterior dejó en L1.
        return credenciales.leerActual(usuarioId).map(fila -> new Credencial(
                fila.getUsuarioId(), fila.getUsuarioId(),
                new HashContrasena(fila.getPasswordHash(), AlgoritmoHash.desdeHashCodificado(fila.getPasswordHash())),
                fila.getActualizadoEn().toInstant(), fila.getVersion()));
    }
    @Override public boolean coincideRevision(RevisionCredencial revision) {
        return revision.usuarioId().equals(revision.credencialId())
                && credenciales.existeRevision(revision.usuarioId(), revision.version());
    }
    @Override public PasswordHistorial historial(UUID usuarioId, Credencial actual) {
        // Actual + como máximo cuatro anteriores, aunque la fixture histórica no incluyera la actual.
        List<PasswordHistorial.Entrada> anteriores = PasswordHistorialPersistenceMapper.aDominio(
                historiales.findTop5ByUsuarioIdOrderByCreadoEnDescIdDesc(usuarioId)).entradas();
        var ventana = anteriores.stream()
                .filter(e -> !e.hash().valor().equals(actual.hashActual().valor()))
                .sorted(java.util.Comparator.comparing(PasswordHistorial.Entrada::creadoEn).reversed()
                    .thenComparing(e -> e.id().toString(), java.util.Comparator.reverseOrder()))
                .limit(4).toList();
        return new PasswordHistorial(ventana);
    }
}
