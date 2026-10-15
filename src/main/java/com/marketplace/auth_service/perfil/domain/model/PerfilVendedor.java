package com.marketplace.auth_service.perfil.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Atributos de VENDEDOR (perfil_vendedor). "tienda" es un nombre, no una referencia. */
public final class PerfilVendedor {
    private final UUID usuarioId;
    private final String codigoVendedor;
    private final String tienda;
    private final LocalDate fechaIngreso;

    public PerfilVendedor(UUID usuarioId, String codigoVendedor, String tienda, LocalDate fechaIngreso) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.codigoVendedor = codigoVendedor;
        this.tienda = tienda;
        this.fechaIngreso = fechaIngreso;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public Optional<String> codigoVendedor() {
        return Optional.ofNullable(codigoVendedor);
    }

    public Optional<String> tienda() {
        return Optional.ofNullable(tienda);
    }

    public Optional<LocalDate> fechaIngreso() {
        return Optional.ofNullable(fechaIngreso);
    }
}
