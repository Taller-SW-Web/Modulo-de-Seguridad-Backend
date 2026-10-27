package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Lo que la verificación necesita de autorización (Sergio), mensajería y auditoría (Christian). */
public interface EfectosVerificacionPort {

    /** Ejecuta el trabajo en una transacción; si {@code confirmar} lo rechaza, revierte todo. */
    <T> T enTransaccion(Supplier<T> trabajo, Predicate<T> confirmar);

    /** {@code usuario.creado} por el outbox, en la transacción de la activación (RF-02.5). */
    void publicarUsuarioCreado(UUID usuarioId, Instant ahora);

    /**
     * {@code USUARIO_VERIFICADO} no crítico (RF-02.7): el éxito y los enlaces vencidos o usados.
     *
     * @param motivo {@code null} si se verificó; si no, el código del 410
     */
    void auditarVerificacion(UUID usuarioId, String motivo, ContextoPeticion contexto);
}
