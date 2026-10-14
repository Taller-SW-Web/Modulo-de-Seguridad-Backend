package com.marketplace.auth_service.shared.domain;

import java.util.Set;
import java.util.UUID;

public record ActorUsuario(
    UUID sub,
    String jti,
    Set<String> roles,
    Set<String> permisos
) {}