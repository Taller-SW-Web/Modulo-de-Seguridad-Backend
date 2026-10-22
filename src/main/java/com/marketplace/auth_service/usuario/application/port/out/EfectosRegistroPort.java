package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Lo que el registro necesita de credenciales (Juan José), autorización (Sergio) y auditoría (Christian). */
public interface EfectosRegistroPort {

    /** Ejecuta el alta en una transacción; si el resultado no es {@code Creada}, revierte todo. */
    ResultadoRegistro enTransaccion(Supplier<ResultadoRegistro> trabajo);

    /** Reglas de SPEC-07 que incumple la contraseña; vacía si la cumple. La cuenta ya debe existir. */
    List<ResultadoRegistro.ReglaIncumplida> validarContrasena(UUID usuarioId, String contrasena);

    void crearCredencial(UUID usuarioId, String contrasena);

    void asignarRolCliente(UUID usuarioId);

    /** {@code USUARIO_CREADO} crítico, en la transacción del alta (RF-01.6, RF-12.6). */
    void auditarCreacion(UUID usuarioId, String canalOrigen, ContextoPeticion contexto);

    /**
     * {@code USUARIO_CREADO} fallido, sin identificar la cuenta existente (RF-01.6).
     *
     * @param canalOrigen {@code null} si el recibido no era válido
     * @param campos      campos inválidos de un rechazo {@code VALIDACION}; vacía en los demás
     */
    void auditarRechazo(String motivo, String canalOrigen, List<String> campos, ContextoPeticion contexto);
}
