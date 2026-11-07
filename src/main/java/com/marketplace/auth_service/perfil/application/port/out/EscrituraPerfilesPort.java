package com.marketplace.auth_service.perfil.application.port.out;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Escritura propia de perfil_cliente / perfil_vendedor (crea la fila si falta). El documento
 * no se toca aquí: guardarlo exige la clave AES externa.
 */
public interface EscrituraPerfilesPort {
    void guardarFechaNacimiento(UUID usuarioId, LocalDate fechaNacimiento);

    /** Recibe los valores finales ya combinados con los actuales; null deja el dato sin registrar. */
    void guardarVendedor(UUID usuarioId, String codigoVendedor, String tienda, LocalDate fechaIngreso);
}
