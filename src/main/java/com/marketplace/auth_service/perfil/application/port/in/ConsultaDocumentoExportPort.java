package com.marketplace.auth_service.perfil.application.port.in;

import com.marketplace.auth_service.perfil.application.dto.DocumentoClaro;
import com.marketplace.auth_service.perfil.application.dto.DocumentoProyectado;
import java.util.Optional;
import java.util.UUID;

/**
 * Capacidad exportada de perfil para consumidores externos autorizados por
 * scope de servicio (SPEC-18 RF-18.4), no por permiso de persona: la
 * autorización ya la resolvió el llamador antes de invocar este puerto.
 */
public interface ConsultaDocumentoExportPort {

    /** Documento del cliente, ya enmascarado (los últimos tres dígitos). Vacío si no tiene uno registrado. */
    Optional<DocumentoProyectado> proyectar(UUID usuarioId);

    /** Documento en claro; solo para un consumidor con el scope que lo autoriza explícitamente. */
    Optional<DocumentoClaro> leerEnClaro(UUID usuarioId);
}
