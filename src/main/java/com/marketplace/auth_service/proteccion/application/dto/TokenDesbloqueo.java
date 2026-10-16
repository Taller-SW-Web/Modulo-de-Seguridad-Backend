package com.marketplace.auth_service.proteccion.application.dto;
import java.time.Instant;
import java.util.UUID;
/** Proyección interna que el puente futuro recibirá de TokensUnUsoPort. */
public record TokenDesbloqueo(UUID id, UUID usuarioId, String proposito, UUID contextoId, Instant expiraEn, boolean usado) { }
