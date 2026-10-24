package com.marketplace.auth_service.direccion.application.service;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort;
import com.marketplace.auth_service.direccion.application.port.out.AuditoriaDireccionesPort;
import com.marketplace.auth_service.direccion.application.port.out.AutorizacionDireccionesPort;
import com.marketplace.auth_service.direccion.application.port.out.CuentaDireccionesPort;
import com.marketplace.auth_service.direccion.application.port.out.DireccionesPersistidasPort;
import com.marketplace.auth_service.direccion.application.port.out.EventosDireccionesPort;
import com.marketplace.auth_service.direccion.domain.model.DireccionNoEncontradaException;
import com.marketplace.auth_service.direccion.domain.model.DireccionNoPermitidaException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Núcleo de FEAT-04-T1. No conoce Spring: la transacción la abre el decorador
 * {@code DireccionesPortTransaccional} (adapter/in/transaction), como en perfil.
 */
public class DireccionesService implements DireccionesPort {
    private final DireccionesPersistidasPort persistencia;
    private final CuentaDireccionesPort cuentas;
    private final EventosDireccionesPort eventos;
    private final AutorizacionDireccionesPort autorizacion;
    private final AuditoriaDireccionesPort auditoria;

    public DireccionesService(DireccionesPersistidasPort persistencia, CuentaDireccionesPort cuentas,
            EventosDireccionesPort eventos, AutorizacionDireccionesPort autorizacion,
            AuditoriaDireccionesPort auditoria) {
        this.persistencia = Objects.requireNonNull(persistencia);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.eventos = Objects.requireNonNull(eventos);
        this.autorizacion = Objects.requireNonNull(autorizacion);
        this.auditoria = Objects.requireNonNull(auditoria);
    }

    @Override
    public List<DireccionDatos> listar(ActorUsuario actor, UUID usuarioId) {
        if (actor == null || !autorizacion.puedeVer(actor, usuarioId)) throw new DireccionNoPermitidaException();
        if (!cuentas.existe(usuarioId)) throw new DireccionNoEncontradaException();
        return persistencia.listar(usuarioId);
    }

    @Override
    public DireccionDatos crear(ActorUsuario actor, UUID usuarioId, DatosNuevaDireccion datos,
            ContextoPeticion contexto) {
        if (actor == null || !autorizacion.puedeAgregar(actor, usuarioId)) throw new DireccionNoPermitidaException();
        if (!cuentas.existe(usuarioId)) throw new DireccionNoEncontradaException();
        // Bloquea la fila de cuenta incluso cuando todavía no hay direcciones: serializa altas concurrentes.
        cuentas.bloquear(usuarioId);
        DireccionDatos creada = persistencia.crear(usuarioId, datos);
        eventos.direccionesActualizadas(usuarioId);
        auditoria.direccionAgregada(actor, usuarioId, contexto);
        return creada;
    }
}
