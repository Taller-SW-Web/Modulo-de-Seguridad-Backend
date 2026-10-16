package com.marketplace.auth_service.credencial.application.port.in;

import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import java.util.UUID;

/**
 * Credencial inicial de una cuenta de prueba (EP-01-T007). Solo existe cuando la
 * carga de cuentas de prueba está habilitada. Requiere la transacción del llamador.
 */
public interface CredencialPruebaInterna {
    /**
     * Si la cuenta aún no tiene credencial, guarda el hash del secreto como credencial
     * actual y primera entrada del historial, y devuelve {@code true}. Si ya tiene una,
     * no la cambia y devuelve {@code false}.
     */
    boolean asegurar(UUID usuarioId, SecretoEfimero secreto);
}
