package com.marketplace.auth_service.usuario.application.dto;

import com.marketplace.auth_service.usuario.domain.model.CodigoRol;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.util.Set;
import java.util.UUID;

public record FiltroCuenta(
    EstadoCuenta estado,
    CodigoRol rol,
    String correoFragmento
) {}