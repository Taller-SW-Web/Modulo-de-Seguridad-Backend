package com.marketplace.auth_service.perfil.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Atributos de CLIENTE (perfil_cliente). Ausente significa sin registrar; no se inventan valores. */
public final class PerfilCliente {
    private final UUID usuarioId;
    private final LocalDate fechaNacimiento;
    private final DocumentoProtegido documento;

    public PerfilCliente(UUID usuarioId, LocalDate fechaNacimiento, DocumentoProtegido documento) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.fechaNacimiento = fechaNacimiento;
        this.documento = documento;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public Optional<LocalDate> fechaNacimiento() {
        return Optional.ofNullable(fechaNacimiento);
    }

    public Optional<DocumentoProtegido> documento() {
        return Optional.ofNullable(documento);
    }
}
