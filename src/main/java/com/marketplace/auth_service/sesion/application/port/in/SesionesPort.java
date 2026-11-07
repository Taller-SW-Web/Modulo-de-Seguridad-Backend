package com.marketplace.auth_service.sesion.application.port.in;

import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.dto.CierreResultado;
import com.marketplace.auth_service.sesion.application.dto.ContextoSesion;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.util.UUID;

/**
 * Capacidad exportada de sesiones (ACUERDOS-INTERNOS §2, EP-02-T003). Recibe
 * identidad ya resuelta; no inyecta LoginService ni MfaService.
 *
 * <p>T003 entrega abrir, resolución por jti y revocaciones. T004 añade
 * {@code renovar} y T005 {@code cerrar}, conservando estas firmas.
 */
public interface SesionesPort {
    SesionIniciada abrir(UUID usuarioId, IdentidadAutorizada identidad, ContextoPeticion contexto);
    ContextoSesion resolverAcceso(AccesoVerificado acceso);
    RenovacionResultado renovar(SecretoRefresco secreto);

    /**
     * Cierra la familia del refresco presentado (FEAT-12-T2). El contexto de la
     * petición viaja para auditar {@code SESION_CERRADA} con ip y agente.
     */
    CierreResultado cerrar(SecretoRefresco secreto, ContextoPeticion contexto);
    void revocarTodas(UUID usuarioId);
    void revocarOtras(UUID usuarioId, UUID familiaActual);
}
