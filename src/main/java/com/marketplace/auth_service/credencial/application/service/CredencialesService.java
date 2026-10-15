package com.marketplace.auth_service.credencial.application.service;

import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.out.*;
import com.marketplace.auth_service.credencial.domain.model.HashContrasena;
import com.marketplace.auth_service.shared.domain.Rol;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CredencialesService implements CredencialesPort {
    private final PersistenciaCredencialesPort persistencia;
    private final PasswordHasherPort hasher;
    private final HashContrasena equivalente;
    private final PoliticaPasswordService politica;

    public CredencialesService(PersistenciaCredencialesPort persistencia, PasswordHasherPort hasher,
            HashContrasena equivalente, PoliticaPasswordService politica) {
        this.persistencia = Objects.requireNonNull(persistencia);
        this.hasher = Objects.requireNonNull(hasher);
        this.equivalente = Objects.requireNonNull(equivalente);
        this.politica = Objects.requireNonNull(politica);
    }
    @Override public PreverificacionCredencial preverificar(Optional<UUID> usuarioId, SecretoEfimero secreto) {
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(secreto);
        var credencial = usuarioId.flatMap(persistencia::leer);
        if (credencial.isEmpty()) {
            hasher.verificar(secreto.valor(), equivalente);
            return new PreverificacionCredencial(false, Optional.empty());
        }
        var actual = credencial.orElseThrow();
        boolean coincide;
        try {
            coincide = hasher.verificar(secreto.valor(), actual.hashActual());
        } catch (IllegalArgumentException invalido) {
            // Un hash legacy malformado no debe exponer el hash ni permitir acceso.
            hasher.verificar(secreto.valor(), equivalente);
            coincide = false;
        }
        return new PreverificacionCredencial(coincide,
                Optional.of(new RevisionCredencial(actual.usuarioId(), actual.id(), actual.version())));
    }
    @Override public boolean confirmarRevision(RevisionCredencial revision) {
        return persistencia.coincideRevision(Objects.requireNonNull(revision));
    }
    @Override public boolean evaluarCaducidad(UUID usuarioId, Set<Rol> roles, Instant instante) {
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(roles);
        Objects.requireNonNull(instante);
        var actual = persistencia.leer(usuarioId)
                .orElseThrow(com.marketplace.auth_service.credencial.domain.exception.PoliticaNoDisponibleException::new);
        return roles.stream().anyMatch(Rol::esDeGestion)
                && !instante.isBefore(actual.actualizadoEn().plus(Duration.ofDays(90)));
    }
    @Override public ValidacionPassword validarNueva(UUID usuarioId, SecretoEfimero secreto) {
        return politica.validar(usuarioId, secreto);
    }
}
