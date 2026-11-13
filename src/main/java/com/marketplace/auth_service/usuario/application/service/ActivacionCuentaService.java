package com.marketplace.auth_service.usuario.application.service;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoActivacion;
import com.marketplace.auth_service.usuario.application.port.in.ActivacionCuentaPort;
import com.marketplace.auth_service.usuario.application.port.out.CuentasPersonalPort;
import com.marketplace.auth_service.usuario.application.port.out.EfectosActivacionPort;
import com.marketplace.auth_service.usuario.application.port.out.TokensActivacionPort;
import com.marketplace.auth_service.usuario.domain.model.TokenVerificacion;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Activación de una cuenta creada por otra persona (SPEC-03 RF-03.3 a RF-03.5; FEAT-13-T1). En una
 * transacción: enlace vigente → cuenta bloqueada y aún sin activar → política de SPEC-07 → credencial
 * → {@code ACTIVO} con el correo verificado y los términos aceptados → token consumido →
 * {@code CUENTA_ACTIVADA} crítico y {@code usuario.creado}. Cualquier rechazo revierte: con una
 * contraseña que no cumple la política la cuenta sigue pendiente y el enlace sigue sirviendo (ESC-03.4).
 */
public final class ActivacionCuentaService implements ActivacionCuentaPort {

    private final TokensActivacionPort tokens;
    private final CuentasPersonalPort cuentas;
    private final EfectosActivacionPort efectos;
    private final String versionTerminos;
    private final Clock reloj;

    public ActivacionCuentaService(TokensActivacionPort tokens, CuentasPersonalPort cuentas,
                                   EfectosActivacionPort efectos, String versionTerminos, Clock reloj) {
        this.tokens = Objects.requireNonNull(tokens);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.efectos = Objects.requireNonNull(efectos);
        this.versionTerminos = Objects.requireNonNull(versionTerminos);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public ResultadoActivacion activar(String tokenClaro, String contrasena, ContextoPeticion contexto) {
        Objects.requireNonNull(tokenClaro);
        Objects.requireNonNull(contrasena);
        Objects.requireNonNull(contexto);
        var token = tokens.localizar(tokenClaro).orElse(null);
        if (token == null) {
            return ResultadoActivacion.Estado.INVALIDA; // sin cuenta conocida: no hay a quién atribuir el intento
        }
        Instant ahora = reloj.instant();
        var resultado = efectos.enTransaccion(() -> activarEnTransaccion(token, contrasena, contexto, ahora),
                r -> r == ResultadoActivacion.Estado.ACTIVADA);
        switch (resultado) {
            case ResultadoActivacion.Estado.ACTIVADA -> { }
            case ResultadoActivacion.Estado.EXPIRADA ->
                    efectos.auditarFallo(token.usuarioId(), "ENLACE_EXPIRADO", contexto, ahora);
            case ResultadoActivacion.Estado.YA_USADA, ResultadoActivacion.Estado.INVALIDA ->
                    efectos.auditarFallo(token.usuarioId(), "ENLACE_YA_USADO", contexto, ahora);
            case ResultadoActivacion.PoliticaIncumplida incumplida ->
                    efectos.auditarFallo(token.usuarioId(), "POLITICA_INCUMPLIDA", contexto, ahora);
        }
        return resultado;
    }

    private ResultadoActivacion activarEnTransaccion(TokenVerificacion token, String contrasena,
                                                     ContextoPeticion contexto, Instant ahora) {
        if (token.usado()) return ResultadoActivacion.Estado.YA_USADA;
        if (!token.vigenteEn(ahora)) return ResultadoActivacion.Estado.EXPIRADA;
        // Cuenta antes que token: dos activaciones simultáneas se ordenan por la fila de la cuenta.
        if (!cuentas.bloquearSinActivar(token.usuarioId())) return ResultadoActivacion.Estado.YA_USADA;
        var reglas = efectos.validarContrasena(token.usuarioId(), contrasena);
        if (!reglas.isEmpty()) return new ResultadoActivacion.PoliticaIncumplida(reglas);
        efectos.crearCredencial(token.usuarioId(), contrasena);
        cuentas.activar(token.usuarioId(), versionTerminos, ahora);
        if (!tokens.consumir(token.id(), token.usuarioId(), ahora)) return ResultadoActivacion.Estado.YA_USADA;
        efectos.registrarActivacion(token.usuarioId(), contexto, ahora);
        return ResultadoActivacion.Estado.ACTIVADA;
    }
}
