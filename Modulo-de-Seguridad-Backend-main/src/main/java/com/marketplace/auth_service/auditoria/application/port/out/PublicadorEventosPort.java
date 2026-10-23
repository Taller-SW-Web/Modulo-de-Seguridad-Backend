package com.marketplace.auth_service.auditoria.application.port.out;

import java.util.Map;

/**
 * Publica un evento en el exchange {@code seguridad.usuarios} con su tipo como routing key
 * (catálogo de eventos). Vuelve solo cuando el broker confirmó; si no, lanza una excepción y el
 * outbox reintenta. Nunca se llama bajo locks de dominio.
 */
public interface PublicadorEventosPort {
    void publicar(String tipo, Map<String, Object> sobre);
}
