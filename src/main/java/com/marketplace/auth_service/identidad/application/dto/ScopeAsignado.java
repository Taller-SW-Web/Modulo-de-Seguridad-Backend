package com.marketplace.auth_service.identidad.application.dto;

/** Scope concedido a un cliente técnico, con la API dueña que irá en {@code aud} (SPEC-19). */
public record ScopeAsignado(String codigo, String audiencia) {
}
