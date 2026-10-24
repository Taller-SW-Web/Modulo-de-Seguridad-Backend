package com.marketplace.auth_service.usuario.application.service;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.dto.SolicitudRegistro;
import com.marketplace.auth_service.usuario.application.port.in.RegistroClientePort;
import com.marketplace.auth_service.usuario.application.port.out.AltaCuentaPort;
import com.marketplace.auth_service.usuario.application.port.out.CorreoVerificacionPort;
import com.marketplace.auth_service.usuario.application.port.out.EfectosRegistroPort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import com.marketplace.auth_service.usuario.domain.model.CanalOrigen;
import com.marketplace.auth_service.usuario.domain.model.CuentaNueva;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro público de clientes (SPEC-01, FEAT-01-T1/T2). En una transacción: identidad
 * {@code PENDIENTE_VERIFICACION} → política de SPEC-07 (necesita la cuenta para la regla
 * de datos personales) → credencial → rol {@code CLIENTE} → auditoría crítica → token de
 * verificación y su correo por el outbox (SPEC-02 RF-02.1). Cualquier rechazo revierte todo
 * y se audita aparte. Nunca emite tokens de sesión (RF-01.7).
 */
public final class RegistroClienteService implements RegistroClientePort {

    private final AltaCuentaPort altas;
    private final EfectosRegistroPort efectos;
    private final CorreoVerificacionPort correoVerificacion;
    private final String versionTerminos;
    private final Clock reloj;

    public RegistroClienteService(AltaCuentaPort altas, EfectosRegistroPort efectos,
                                  CorreoVerificacionPort correoVerificacion, String versionTerminos, Clock reloj) {
        this.altas = Objects.requireNonNull(altas);
        this.efectos = Objects.requireNonNull(efectos);
        this.correoVerificacion = Objects.requireNonNull(correoVerificacion);
        this.versionTerminos = Objects.requireNonNull(versionTerminos);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public ResultadoRegistro registrar(SolicitudRegistro solicitud, ContextoPeticion contexto) {
        Objects.requireNonNull(solicitud);
        Objects.requireNonNull(contexto);
        var canal = CanalOrigen.valueOf(solicitud.canalOrigen());
        var cuenta = new CuentaNueva(UUID.randomUUID(), solicitud.correo(), solicitud.nombres(),
                solicitud.apellidos(), solicitud.celular(), canal, versionTerminos, reloj.instant());
        ResultadoRegistro resultado;
        try {
            resultado = efectos.enTransaccion(() -> {
                if (!altas.crear(cuenta)) {
                    return ResultadoRegistro.Rechazo.CORREO_NO_DISPONIBLE;
                }
                var reglas = efectos.validarContrasena(cuenta.id(), solicitud.contrasena());
                if (!reglas.isEmpty()) {
                    return new ResultadoRegistro.PoliticaIncumplida(reglas);
                }
                efectos.crearCredencial(cuenta.id(), solicitud.contrasena());
                efectos.asignarRolCliente(cuenta.id());
                efectos.auditarCreacion(cuenta.id(), canal.name(), contexto);
                correoVerificacion.enviar(cuenta.id(), cuenta.correo(), cuenta.nombres(), canal);
                return new ResultadoRegistro.Creada(cuenta.id());
            });
        } catch (VerificacionNoDisponibleException noDisponible) {
            // La excepción revirtió la transacción: no queda una cuenta que nunca recibirá su enlace.
            efectos.auditarRechazo("NO_DISPONIBLE", canal.name(), List.of(), contexto);
            throw noDisponible;
        }
        switch (resultado) {
            case ResultadoRegistro.Rechazo rechazo ->
                    efectos.auditarRechazo(rechazo.name(), canal.name(), List.of(), contexto);
            case ResultadoRegistro.PoliticaIncumplida ignorada ->
                    efectos.auditarRechazo("POLITICA_INCUMPLIDA", canal.name(), List.of(), contexto);
            case ResultadoRegistro.Creada creada -> { }
        }
        return resultado;
    }

    @Override
    public void auditarSolicitudInvalida(List<String> campos, String canalOrigen, ContextoPeticion contexto) {
        Objects.requireNonNull(contexto);
        efectos.auditarRechazo("VALIDACION", canalOrigen, List.copyOf(campos), contexto);
    }
}
