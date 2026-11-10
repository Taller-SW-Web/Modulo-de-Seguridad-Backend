package com.marketplace.auth_service.identidad.application.dto;

/** Scope del catálogo que se puede conceder (SPEC-19, RF-19.3). */
public record ScopeDisponible(String codigo, String audiencia, String descripcion) {
}
