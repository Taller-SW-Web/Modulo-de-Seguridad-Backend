package com.marketplace.auth_service.credencial.application.dto;

import java.util.Objects;

public record InfraccionPassword(ReglaPassword regla, Integer esperado) {
    public InfraccionPassword { Objects.requireNonNull(regla, "regla"); }
}
