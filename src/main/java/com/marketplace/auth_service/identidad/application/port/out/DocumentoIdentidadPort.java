package com.marketplace.auth_service.identidad.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Puente hacia el documento del cliente (SPEC-18 RF-18.4): el dueño es {@code perfil}. */
public interface DocumentoIdentidadPort {

    /** Enmascarado (últimos tres dígitos); vacío si no tiene documento registrado. */
    Optional<String> proyectar(UUID usuarioId);

    /** En claro; solo se invoca cuando el actor tiene el scope que lo autoriza. */
    Optional<String> leerEnClaro(UUID usuarioId);
}
