package com.marketplace.auth_service.sesion.adapter.in.transaction;

import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.dto.ContextoSesion;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Frontera transaccional de {@link SesionesPort} (BACKEND-HEXAGONAL §4). Es el
 * único bean del puerto: nadie llama al servicio sin pasar por aquí.
 *
 * <ul>
 *   <li>Mutaciones ({@code abrir}, {@code renovar}, revocaciones): REQUIRED con
 *       READ_COMMITTED, así se unen al commit del coordinador (login/MFA) o
 *       abren el propio.</li>
 *   <li>{@code resolverAcceso}: lectura readOnly; el lock de la raíz lo toma el
 *       adaptador dentro de la transacción del llamador.</li>
 * </ul>
 */
public class SesionTransaccional implements SesionesPort {

    private final SesionesPort casoDeUso;

    public SesionTransaccional(SesionesPort casoDeUso) {
        this.casoDeUso = casoDeUso;
    }

    @Override
    @Transactional
    public SesionIniciada abrir(UUID usuarioId, IdentidadAutorizada identidad, ContextoPeticion contexto) {
        return casoDeUso.abrir(usuarioId, identidad, contexto);
    }

    @Override
    @Transactional(readOnly = true)
    public ContextoSesion resolverAcceso(AccesoVerificado acceso) {
        return casoDeUso.resolverAcceso(acceso);
    }

    @Override
    @Transactional
    public RenovacionResultado renovar(SecretoRefresco secreto) {
        return casoDeUso.renovar(secreto);
    }

    @Override
    @Transactional
    public void revocarTodas(UUID usuarioId) {
        casoDeUso.revocarTodas(usuarioId);
    }

    @Override
    @Transactional
    public void revocarOtras(UUID usuarioId, UUID familiaActual) {
        casoDeUso.revocarOtras(usuarioId, familiaActual);
    }
}
