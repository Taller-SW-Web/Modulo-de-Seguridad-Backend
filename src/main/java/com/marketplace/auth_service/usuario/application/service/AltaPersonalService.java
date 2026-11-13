package com.marketplace.auth_service.usuario.application.service;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAltaPersonal;
import com.marketplace.auth_service.usuario.application.dto.SolicitudAltaPersonal;
import com.marketplace.auth_service.usuario.application.port.in.AltaPersonalPort;
import com.marketplace.auth_service.usuario.application.port.out.CorreoActivacionPort;
import com.marketplace.auth_service.usuario.application.port.out.CuentasPersonalPort;
import com.marketplace.auth_service.usuario.application.port.out.EfectosAltaPersonalPort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import com.marketplace.auth_service.usuario.domain.model.CuentaPersonalNueva;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Alta administrativa de personal (SPEC-03 RF-03.1, RF-03.4 y RF-03.5; FEAT-13-T1). En una transacción:
 * permiso vigente → identidad {@code PENDIENTE_VERIFICACION} sin contraseña ni términos → rol inicial,
 * si lo hay → {@code USUARIO_CREADO} crítico → token de activación de 72 horas y su correo por el
 * outbox. Cualquier rechazo revierte todo y se audita aparte. No publica {@code usuario.creado}: eso
 * ocurre al activarse la cuenta (ESC-03.1).
 */
public final class AltaPersonalService implements AltaPersonalPort {

    private final CuentasPersonalPort cuentas;
    private final EfectosAltaPersonalPort efectos;
    private final CorreoActivacionPort correoActivacion;
    private final Clock reloj;

    public AltaPersonalService(CuentasPersonalPort cuentas, EfectosAltaPersonalPort efectos,
                               CorreoActivacionPort correoActivacion, Clock reloj) {
        this.cuentas = Objects.requireNonNull(cuentas);
        this.efectos = Objects.requireNonNull(efectos);
        this.correoActivacion = Objects.requireNonNull(correoActivacion);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public boolean autorizar(ActorUsuario actor, ContextoPeticion contexto) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(contexto);
        if (efectos.puedeCrear(actor.sub())) return true;
        efectos.denegado(actor, contexto, reloj.instant());
        return false;
    }

    @Override
    public ResultadoAltaPersonal crear(ActorUsuario actor, SolicitudAltaPersonal solicitud, ContextoPeticion contexto) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(solicitud);
        Objects.requireNonNull(contexto);
        var ahora = reloj.instant();
        var cuenta = new CuentaPersonalNueva(UUID.randomUUID(), solicitud.correo(), solicitud.nombres(),
                solicitud.apellidos(), solicitud.rol(), ahora);
        ResultadoAltaPersonal resultado;
        try {
            resultado = efectos.enTransaccion(() -> {
                // Reautoriza dentro de la transacción: el permiso pudo retirarse después de autorizar().
                if (!efectos.puedeCrear(actor.sub())) return ResultadoAltaPersonal.Rechazo.SIN_PERMISO;
                if (!cuentas.crear(cuenta)) return ResultadoAltaPersonal.Rechazo.CORREO_NO_DISPONIBLE;
                if (cuenta.rol() != null) efectos.asignarRolInicial(cuenta.id(), cuenta.rol());
                efectos.auditarCreacion(cuenta.id(), cuenta.rol(), actor, contexto, ahora);
                correoActivacion.enviar(cuenta.id(), cuenta.correo(), cuenta.nombres());
                return new ResultadoAltaPersonal.Creada(cuenta.id());
            }, r -> r instanceof ResultadoAltaPersonal.Creada);
        } catch (VerificacionNoDisponibleException noDisponible) {
            // La excepción revirtió el alta: no queda una cuenta que nunca recibirá su enlace.
            efectos.auditarRechazo("NO_DISPONIBLE", actor, contexto, ahora);
            throw noDisponible;
        }
        switch (resultado) {
            case ResultadoAltaPersonal.Rechazo.SIN_PERMISO -> efectos.denegado(actor, contexto, ahora);
            case ResultadoAltaPersonal.Rechazo.CORREO_NO_DISPONIBLE ->
                    efectos.auditarRechazo("CORREO_NO_DISPONIBLE", actor, contexto, ahora);
            case ResultadoAltaPersonal.Creada creada -> { }
        }
        return resultado;
    }
}
