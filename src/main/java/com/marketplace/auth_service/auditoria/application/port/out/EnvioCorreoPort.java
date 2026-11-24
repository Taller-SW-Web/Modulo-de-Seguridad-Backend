package com.marketplace.auth_service.auditoria.application.port.out;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo.Plantilla;

/** Transporte SMTP real; nunca se llama bajo locks de dominio. */
public interface EnvioCorreoPort {
    void enviar(String destino, String asunto, String cuerpo);

    default void enviarConPlantilla(String destino, String asunto, String cuerpo, Plantilla plantilla) {
        enviar(destino, asunto, cuerpo);
    }
}
