package com.marketplace.auth_service.proteccion.application.dto;
import java.time.Instant;
import java.util.UUID;
/** Clasificación real para login, refresh y lectura autorizada; sin huellas/pepper. */
public record EvaluacionProteccion(String estado, boolean permiteLogin, boolean permiteRenovar,
        String tipoBloqueo, Instant bloqueadoHasta, UUID revisionBloqueo,
        int intentosFallidos, int bloqueosSeguidos) { }
