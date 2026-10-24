package com.marketplace.auth_service.credencial.application.service;

import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.in.RestablecerContrasenaPort;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.credencial.application.port.out.EfectosRestablecimientoPort;
import com.marketplace.auth_service.credencial.application.port.out.EscrituraCredencialPort;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.util.Objects;

/** Caso de uso FEAT-03-T3. El token claro y la contraseña nunca se registran. */
public final class RestablecerContrasenaService implements RestablecerContrasenaPort {
    private final TokensUnUsoPort tokens;
    private final CredencialesPort credenciales;
    private final PasswordHasherPort hasher;
    private final EscrituraCredencialPort escritura;
    private final EfectosRestablecimientoPort efectos;
    private final Clock reloj;

    public RestablecerContrasenaService(TokensUnUsoPort tokens, CredencialesPort credenciales,
            PasswordHasherPort hasher, EscrituraCredencialPort escritura,
            EfectosRestablecimientoPort efectos, Clock reloj) {
        this.tokens=Objects.requireNonNull(tokens); this.credenciales=Objects.requireNonNull(credenciales);
        this.hasher=Objects.requireNonNull(hasher); this.escritura=Objects.requireNonNull(escritura);
        this.efectos=Objects.requireNonNull(efectos); this.reloj=Objects.requireNonNull(reloj);
    }

    @Override public ResultadoRestablecimiento restablecer(SecretoEfimero tokenClaro,
            SecretoEfimero nuevaContrasena, ContextoPeticion contexto) {
        Objects.requireNonNull(tokenClaro); Objects.requireNonNull(nuevaContrasena); Objects.requireNonNull(contexto);
        String hashToken=TokenUnUsoHashing.sha256Hex(tokenClaro.valor());
        TokenUnUsoSnapshot orientativo=tokens.localizar(hashToken,PropositoToken.RECUPERACION).orElse(null);
        if (orientativo==null || orientativo.usado()) return ResultadoRestablecimiento.Simple.TOKEN_INVALIDO;
        if (!reloj.instant().isBefore(orientativo.expiraEn())) return ResultadoRestablecimiento.Simple.TOKEN_EXPIRADO;

        // Argon2 se calcula antes de mantener locks/conexión. La política se revisa dentro
        // de la transacción, después de bloquear la cuenta, para observar el historial vigente.
        String hashNuevo=hasher.generar(nuevaContrasena.valor()).valor();
        return efectos.enTransaccion(() -> {
            efectos.bloquearCuenta(orientativo.usuarioId());
            efectos.prepararProteccion(orientativo.usuarioId());
            TokenUnUsoSnapshot actual=tokens.localizar(hashToken,PropositoToken.RECUPERACION).orElse(null);
            if (actual==null || actual.usado() || !actual.id().equals(orientativo.id())
                    || !actual.usuarioId().equals(orientativo.usuarioId()))
                return ResultadoRestablecimiento.Simple.TOKEN_INVALIDO;
            var ahora=reloj.instant();
            if (!ahora.isBefore(actual.expiraEn())) return ResultadoRestablecimiento.Simple.TOKEN_EXPIRADO;
            ValidacionPassword politica=credenciales.validarNueva(actual.usuarioId(),nuevaContrasena);
            if (!politica.valida()) return new ResultadoRestablecimiento.PoliticaIncumplida(politica);
            if (!tokens.consumir(actual.id(),actual.usuarioId(),PropositoToken.RECUPERACION,ahora))
                return ResultadoRestablecimiento.Simple.TOKEN_INVALIDO;
            escritura.reemplazar(actual.usuarioId(),hashNuevo,ahora);
            efectos.restablecerProteccion(actual.usuarioId(),contexto,ahora);
            efectos.revocarSesiones(actual.usuarioId());
            efectos.auditar(actual.usuarioId(),contexto,ahora);
            efectos.avisar(actual.usuarioId());
            return ResultadoRestablecimiento.Simple.RESTABLECIDA;
        });
    }
}
