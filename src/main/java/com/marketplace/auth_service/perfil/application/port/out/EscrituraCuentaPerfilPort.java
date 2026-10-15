package com.marketplace.auth_service.perfil.application.port.out;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import java.util.UUID;

/** Escritura de los básicos de la cuenta, que son de Eva. Perfil no toca su tabla ni su repositorio. */
public interface EscrituraCuentaPerfilPort {
    /** Bloquea la fila de la cuenta hasta el fin de la transacción en curso. */
    void bloquear(UUID usuarioId);

    /** Aplica solo los campos presentes; Eva deja celularVerificado=false si cambia el celular. */
    void actualizarBasicos(UUID usuarioId, CambioAtributos cambio);
}
