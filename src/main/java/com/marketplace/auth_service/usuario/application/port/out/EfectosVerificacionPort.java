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
     * Un cambio de correo confirmado (RF-16.9, RF-16.10), dentro de la transacción del cambio:
     * {@code CORREO_CAMBIADO} crítico y {@code usuario.atributos_actualizados} con {@code "campos": ["correo"]}
     * por el outbox. Ninguno lleva direcciones.
     */
    void registrarCorreoCambiado(UUID usuarioId, Instant ahora, ContextoPeticion contexto);

    /**
     * {@code USUARIO_VERIFICADO} no crítico (RF-02.7): el éxito y los enlaces vencidos o usados.
     *
     * @param motivo {@code null} si se verificó; si no, el código del 410
     */
    void auditarVerificacion(UUID usuarioId, String motivo, ContextoPeticion contexto);
}
