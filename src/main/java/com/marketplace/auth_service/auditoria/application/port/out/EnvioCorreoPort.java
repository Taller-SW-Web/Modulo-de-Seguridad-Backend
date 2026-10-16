package com.marketplace.auth_service.auditoria.application.port.out;

/** Transporte SMTP real; nunca se llama bajo locks de dominio. */
public interface EnvioCorreoPort {
    void enviar(String destino, String asunto, String cuerpo);
}
