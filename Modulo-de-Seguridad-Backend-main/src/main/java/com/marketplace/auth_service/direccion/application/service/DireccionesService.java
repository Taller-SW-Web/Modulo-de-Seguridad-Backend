package com.marketplace.auth_service.direccion.application.service;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort;
import com.marketplace.auth_service.direccion.application.port.out.CuentaDireccionesPort;
import com.marketplace.auth_service.direccion.application.port.out.DireccionesPersistidasPort;
import com.marketplace.auth_service.direccion.application.port.out.EventosDireccionesPort;
import com.marketplace.auth_service.direccion.domain.model.DireccionNoEncontradaException;
import com.marketplace.auth_service.direccion.domain.model.DireccionNoPermitidaException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
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

    public DireccionesService(DireccionesPersistidasPort persistencia, CuentaDireccionesPort cuentas,
            EventosDireccionesPort eventos) {
        this.persistencia = Objects.requireNonNull(persistencia);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.eventos = Objects.requireNonNull(eventos);
    }

    @Override
    public List<DireccionDatos> listar(ActorUsuario actor, UUID usuarioId) {
        exigirTitular(actor, usuarioId);
        if (!cuentas.existe(usuarioId)) throw new DireccionNoEncontradaException();
        return persistencia.listar(usuarioId);
    }

    @Override
    public DireccionDatos crear(ActorUsuario actor, UUID usuarioId, DatosNuevaDireccion datos) {
        exigirTitular(actor, usuarioId);
        if (!cuentas.existe(usuarioId)) throw new DireccionNoEncontradaException();
        // Bloquea la fila de cuenta incluso cuando todavía no hay direcciones: serializa altas concurrentes.
        cuentas.bloquear(usuarioId);
        DireccionDatos creada = persistencia.crear(usuarioId, datos);
        eventos.direccionesActualizadas(usuarioId);
        return creada;
    }

    private static void exigirTitular(ActorUsuario actor, UUID usuarioId) {
        if (actor == null || !actor.esTitularDe(usuarioId)) throw new DireccionNoPermitidaException();
    }
}
