package com.marketplace.auth_service.perfil.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Códigos de rol de una cuenta (CodigoRol del contrato). Solo decide qué atributos de perfil aplican. */
public record RolesCuenta(Set<String> codigos) {
    public static final String CLIENTE = "CLIENTE";
    public static final String VENDEDOR = "VENDEDOR";

    public RolesCuenta {
        codigos = Set.copyOf(Objects.requireNonNull(codigos, "codigos"));
    }

    public boolean esCliente() {
        return codigos.contains(CLIENTE);
    }

    public boolean esVendedor() {
        return codigos.contains(VENDEDOR);
    }

    public List<String> ordenados() {
        return codigos.stream().sorted().toList();
    }
}
