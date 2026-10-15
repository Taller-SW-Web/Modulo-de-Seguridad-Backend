package com.marketplace.auth_service.proteccion.application.dto;
import java.util.UUID;
public record ResultadoFallo(Estado estado, int intentosFallidos, int bloqueosSeguidos, UUID revisionBloqueo) {
    public enum Estado { IGNORADO, REPETIDO, CONTADO, BLOQUEADO }
}
