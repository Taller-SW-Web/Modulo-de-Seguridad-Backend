package com.marketplace.auth_service.sesion.application.service;

import com.marketplace.auth_service.sesion.application.dto.CuentaLogin;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.PreverificacionLogin;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.sesion.application.dto.RevisionLogin;
import com.marketplace.auth_service.sesion.application.dto.RolesLogin;
import com.marketplace.auth_service.sesion.application.dto.VerificacionOtpLogin;
import com.marketplace.auth_service.sesion.application.port.in.LoginPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.CapacidadesLoginPort;
import com.marketplace.auth_service.sesion.application.port.out.TransaccionLoginPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinador del login (EP-02-T006, SPEC-05 RF-05.1–05.7).
 *
 * <ol>
 *   <li>Fuera de locks: cuenta orientativa por correo normalizado y preverificación de la
 *       contraseña (comparación equivalente si la cuenta no existe).</li>
 *   <li>En una transacción, con el orden de locks común: guard ADMIN (solo si hay que
 *       registrar un fallo) → cuenta → protección → credencial → MFA/sesión.</li>
 *   <li>Un fallo elegible se confirma mediante protección antes del 401. Si la revisión de
 *       credencial o el correo cambiaron entre la preverificación y el lock, se repite una
 *       sola vez sin efectos; a la segunda, rechazo.</li>
 *   <li>MFA efectivo: solo desafío, sin familia ni reinicio de la racha. Sin MFA: caducidad
 *       (403), reinicio de la protección y apertura de la familia en el mismo commit.</li>
 * </ol>
 */
public final class LoginService implements LoginPort {

    private final CapacidadesLoginPort capacidades;
    private final SesionesPort sesiones;
    private final TransaccionLoginPort transacciones;
    private final Clock reloj;

    public LoginService(CapacidadesLoginPort capacidades, SesionesPort sesiones,
                        TransaccionLoginPort transacciones, Clock reloj) {
        this.capacidades = Objects.requireNonNull(capacidades);
        this.sesiones = Objects.requireNonNull(sesiones);
        this.transacciones = Objects.requireNonNull(transacciones);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /** Señal interna: la revisión cambió; nunca sale del servicio. */
    private static final Object REPETIR = new Object();

    @Override
    public ResultadoLogin iniciar(String correo, String contrasena, ContextoPeticion contexto) {
        Objects.requireNonNull(contexto, "contexto");
        String normalizado = normalizar(correo);
        String secreto = contrasena == null ? "" : contrasena;
        for (int intento = 0; intento < 2; intento++) {
            Optional<CuentaLogin> orientativa = normalizado.isEmpty()
                    ? Optional.empty() : capacidades.buscarCuenta(normalizado);
            PreverificacionLogin previa = capacidades.preverificar(orientativa.map(CuentaLogin::id), secreto);
            Object resultado = transacciones.ejecutar(
                    () -> enTransaccion(normalizado, orientativa, previa, secreto, contexto));
            if (resultado != REPETIR) {
                return (ResultadoLogin) resultado;
            }
        }
        return ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS;
    }

    private Object enTransaccion(String correo, Optional<CuentaLogin> orientativa, PreverificacionLogin previa,
                                 String secreto, ContextoPeticion contexto) {
        if (orientativa.isEmpty()) {
            capacidades.registrarFallo(Optional.empty(), correo, secreto, contexto);
            capacidades.auditar("SESION_FALLIDA", false, Optional.empty(), contexto);
            return ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS;
        }
        UUID id = orientativa.get().id();
        if (!previa.coincide()) {
            capacidades.bloquearCatalogoAdministrador();
        }
        CuentaLogin cuenta = capacidades.bloquearCuenta(id);
        if (!cuenta.correo().equals(correo)) {
            return REPETIR; // el correo cambió entre la búsqueda y el lock
        }
        if (!previa.coincide()) {
            capacidades.registrarFallo(Optional.of(id), correo, secreto, contexto);
            capacidades.auditar("SESION_FALLIDA", false, Optional.of(id), contexto);
            return ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS;
        }
        Instant ahora = reloj.instant();
        if (!cuenta.disponible() || !capacidades.proteccionPermiteLogin(id, ahora)) {
            capacidades.auditar("SESION_FALLIDA", false, Optional.of(id), contexto);
            return ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS;
        }
        RevisionLogin revision = previa.revision().orElseThrow();
        RolesLogin roles = capacidades.resolverRoles(id);
        if (capacidades.mfaEfectivo(cuenta.mfaVoluntario(), roles)) {
            return capacidades.crearDesafio(id, revision)
                    .<Object>map(ResultadoLogin.DesafioRequerido::new)
                    .orElse(REPETIR);
        }
        if (!capacidades.confirmarRevision(revision)) {
            return REPETIR;
        }
        if (capacidades.contrasenaCaducada(id, roles, ahora)) {
            return ResultadoLogin.Rechazo.CONTRASENA_CADUCADA;
        }
        capacidades.completarLogin(id);
        capacidades.auditar("SESION_INICIADA", true, Optional.of(id), contexto);
        return new ResultadoLogin.Iniciada(sesiones.abrir(id,
                new IdentidadAutorizada(id, cuenta.nombreCompleto(), cuenta.correo(), roles.roles(), roles.permisos()),
                contexto));
    }

    @Override
    public ResultadoLogin completarMfa(String challengeToken, String codigo, ContextoPeticion contexto) {
        Objects.requireNonNull(contexto, "contexto");
        return transacciones.ejecutar(() -> switch (capacidades.verificarOtp(challengeToken, codigo)) {
            case VerificacionOtpLogin.CodigoInvalido invalido -> {
                capacidades.auditar("OTP_FALLIDO", false, Optional.empty(), contexto);
                yield new ResultadoLogin.CodigoInvalido(invalido.intentosRestantes());
            }
            case VerificacionOtpLogin.Rechazo rechazo -> switch (rechazo) {
                case DESAFIO_INVALIDO -> ResultadoLogin.Rechazo.DESAFIO_INVALIDO;
                case INTENTOS_AGOTADOS -> {
                    capacidades.auditar("OTP_FALLIDO", false, Optional.empty(), contexto);
                    yield ResultadoLogin.Rechazo.OTP_INTENTOS_AGOTADOS;
                }
                case CODIGO_EXPIRADO -> ResultadoLogin.Rechazo.OTP_EXPIRADO;
            };
            case VerificacionOtpLogin.Verificada verificada -> completar(verificada, contexto);
        });
    }

    /** Revalida bajo el lock de la cuenta (ya tomado por MFA en esta transacción) y abre la sesión. */
    private ResultadoLogin completar(VerificacionOtpLogin.Verificada verificada, ContextoPeticion contexto) {
        UUID id = verificada.usuarioId();
        CuentaLogin cuenta = capacidades.bloquearCuenta(id);
        Instant ahora = reloj.instant();
        if (!cuenta.disponible() || !capacidades.proteccionPermiteLogin(id, ahora)
                || !capacidades.confirmarRevision(verificada.revision())) {
            return ResultadoLogin.Rechazo.DESAFIO_INVALIDO;
        }
        RolesLogin roles = capacidades.resolverRoles(id);
        if (capacidades.contrasenaCaducada(id, roles, ahora)) {
            return ResultadoLogin.Rechazo.CONTRASENA_CADUCADA;
        }
        capacidades.completarLogin(id);
        capacidades.auditar("OTP_VERIFICADO", true, Optional.of(id), contexto);
        capacidades.auditar("SESION_INICIADA", true, Optional.of(id), contexto);
        return new ResultadoLogin.Iniciada(sesiones.abrir(id,
                new IdentidadAutorizada(id, cuenta.nombreCompleto(), cuenta.correo(), roles.roles(), roles.permisos()),
                contexto));
    }

    static String normalizar(String correo) {
        return correo == null ? "" : correo.strip().toLowerCase(Locale.ROOT);
    }
}
