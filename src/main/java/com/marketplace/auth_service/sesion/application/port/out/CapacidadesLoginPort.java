package com.marketplace.auth_service.sesion.application.port.out;

import com.marketplace.auth_service.sesion.application.dto.CuentaLogin;
import com.marketplace.auth_service.sesion.application.dto.DesafioEmitido;
import com.marketplace.auth_service.sesion.application.dto.PreverificacionLogin;
import com.marketplace.auth_service.sesion.application.dto.RevisionLogin;
import com.marketplace.auth_service.sesion.application.dto.RolesLogin;
import com.marketplace.auth_service.sesion.application.dto.VerificacionOtpLogin;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que el login consume de otras capacidades, resuelto por sus dueños mediante
 * {@code adapter.out.capacidad.PuenteLogin}. Los métodos que bloquean o escriben
 * requieren la transacción del login; {@link #buscarCuenta} y {@link #preverificar}
 * corren antes, sin locks.
 */
public interface CapacidadesLoginPort {

    Optional<CuentaLogin> buscarCuenta(String correoNormalizado);

    PreverificacionLogin preverificar(Optional<UUID> usuarioId, String contrasena);

    /** Guard del catálogo ADMIN: siempre antes del lock de la cuenta (orden de locks). */
    void bloquearCatalogoAdministrador();

    CuentaLogin bloquearCuenta(UUID usuarioId);

    boolean proteccionPermiteLogin(UUID usuarioId, Instant ahora);

    /** Fallo elegible: lo registra y cuenta protección; requiere guard ADMIN y cuenta bloqueada. */
    void registrarFallo(Optional<UUID> usuarioId, String correoIntentado, String contrasena, ContextoPeticion contexto);

    boolean confirmarRevision(RevisionLogin revision);

    RolesLogin resolverRoles(UUID usuarioId);

    boolean mfaEfectivo(boolean voluntario, RolesLogin roles);

    /** Vacío si la revisión cambió o la cuenta dejó de ser válida: el login reintenta una vez. */
    Optional<DesafioEmitido> crearDesafio(UUID usuarioId, RevisionLogin revision);

    boolean contrasenaCaducada(UUID usuarioId, RolesLogin roles, Instant ahora);

    void completarLogin(UUID usuarioId);

    /**
     * Hecho no crítico de sesión/OTP (SESION_INICIADA, SESION_FALLIDA, OTP_VERIFICADO, OTP_FALLIDO):
     * se registra después del commit y su fallo no afecta al login. Nunca lleva secretos.
     */
    void auditar(String accion, boolean exito, java.util.Optional<UUID> usuarioId, ContextoPeticion contexto);

    /** Verifica el código del desafío (MFA, Luis) dentro de la transacción del login. */
    VerificacionOtpLogin verificarOtp(String challengeToken, String codigo);
}
