package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Lo que la activación necesita de credenciales (Juan José), autorización (Sergio), mensajería y auditoría. */
public interface EfectosActivacionPort {

    /** Ejecuta el trabajo en una transacción; si {@code confirmar} lo rechaza, revierte todo. */
    <T> T enTransaccion(Supplier<T> trabajo, Predicate<T> confirmar);

    /** Reglas de SPEC-07 que incumple la contraseña; vacía si la cumple (RF-03.3). */
    List<ResultadoRegistro.ReglaIncumplida> validarContrasena(UUID usuarioId, String contrasena);

    /** Primera credencial de la cuenta; nadie más que su titular llegó a conocerla (A6). */
    void crearCredencial(UUID usuarioId, String contrasena);

    /**
     * {@code CUENTA_ACTIVADA} crítico y {@code usuario.creado} con los roles de la cuenta por el outbox,
     * en la transacción de la activación (RF-03.4, RF-03.5).
     */
    void registrarActivacion(UUID usuarioId, ContextoPeticion contexto, Instant ahora);

    /**
     * {@code CUENTA_ACTIVADA} fallido y no crítico: enlace vencido o usado, o contraseña rechazada.
     *
     * @param motivo código de la respuesta ({@code ENLACE_EXPIRADO}, {@code ENLACE_YA_USADO} o
     *               {@code POLITICA_INCUMPLIDA})
     */
    void auditarFallo(UUID usuarioId, String motivo, ContextoPeticion contexto, Instant ahora);
}
