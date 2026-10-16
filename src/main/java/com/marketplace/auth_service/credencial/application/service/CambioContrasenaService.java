package com.marketplace.auth_service.credencial.application.service;

import com.marketplace.auth_service.credencial.application.dto.ResultadoCambioContrasena;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CambioContrasenaPort;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.out.EfectosCambioContrasenaPort;
import com.marketplace.auth_service.credencial.application.port.out.EscrituraCredencialPort;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Cambio de contraseña (EP-03-T005, SPEC-07). La comparación de la actual y el hash de la
 * nueva (Argon2id, costosos) van fuera de locks; en la transacción: cuenta → revisión de la
 * credencial → familia actual y revocación de otras → escritura + historial → crítica y correo durable.
 */
public final class CambioContrasenaService implements CambioContrasenaPort {

    private final CredencialesPort credenciales;
    private final PasswordHasherPort hasher;
    private final EscrituraCredencialPort escritura;
    private final EfectosCambioContrasenaPort efectos;
    private final Clock reloj;

    public CambioContrasenaService(CredencialesPort credenciales, PasswordHasherPort hasher,
                                   EscrituraCredencialPort escritura, EfectosCambioContrasenaPort efectos, Clock reloj) {
        this.credenciales = Objects.requireNonNull(credenciales);
        this.hasher = Objects.requireNonNull(hasher);
        this.escritura = Objects.requireNonNull(escritura);
        this.efectos = Objects.requireNonNull(efectos);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public ResultadoCambioContrasena cambiar(UUID usuarioId, UUID jti, SecretoEfimero actual, SecretoEfimero nueva,
                                             ContextoPeticion contexto) {
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(contexto);
        var previa = credenciales.preverificar(Optional.of(usuarioId), actual);
        if (!previa.coincide() || previa.revision().isEmpty()) {
            return ResultadoCambioContrasena.Simple.ACTUAL_INCORRECTA;
        }
        var validacion = credenciales.validarNueva(usuarioId, nueva);
        if (!validacion.valida()) {
            return new ResultadoCambioContrasena.PoliticaIncumplida(validacion);
        }
        String hashNuevo = hasher.generar(nueva.valor()).valor();
        var revision = previa.revision().get();
        return efectos.enTransaccion(() -> {
            efectos.bloquearCuenta(usuarioId);
            if (!credenciales.confirmarRevision(revision)) {
                return ResultadoCambioContrasena.Simple.ACTUAL_INCORRECTA; // cambió mientras tanto
            }
            Optional<UUID> familia = jti == null ? Optional.empty() : efectos.familiaDe(usuarioId, jti);
            if (familia.isEmpty()) {
                return ResultadoCambioContrasena.Simple.SESION_INVALIDA;
            }
            efectos.revocarOtras(usuarioId, familia.get());
            escritura.reemplazar(usuarioId, hashNuevo, reloj.instant());
            efectos.auditarCambio(usuarioId, contexto);
            efectos.avisarCambio(usuarioId);
            return ResultadoCambioContrasena.Simple.CAMBIADA;
        });
    }
}
