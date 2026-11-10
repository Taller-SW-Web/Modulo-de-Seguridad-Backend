package com.marketplace.auth_service.identidad.application.port.in;

import com.marketplace.auth_service.identidad.application.dto.ClienteTecnico;
import com.marketplace.auth_service.identidad.application.dto.ResultadoGestionCliente;
import com.marketplace.auth_service.identidad.application.dto.ScopeDisponible;
import com.marketplace.auth_service.identidad.application.dto.SecretoEntregado;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.util.List;

/**
 * Gestión de clientes técnicos por un {@code ADMIN_SISTEMA} (SPEC-19, FEAT-14).
 *
 * <p>Toda operación revalida en la base el permiso {@code integracion.gestionar}
 * antes de mirar si el cliente existe (RF-19.1). Los cambios efectivos se auditan
 * como acciones críticas en la misma transacción (RF-19.13, RF-19.14); las
 * operaciones sin efecto (conceder lo ya concedido, desactivar lo inactivo)
 * responden correcto sin auditar.
 */
public interface ClientesTecnicosPort {

    ResultadoGestionCliente<List<ClienteTecnico>> listar(ActorUsuario actor, ContextoPeticion contexto);

    ResultadoGestionCliente<List<ScopeDisponible>> catalogo(ActorUsuario actor, ContextoPeticion contexto);

    ResultadoGestionCliente<SecretoEntregado> crear(ActorUsuario actor, String clientId, String nombre,
                                                   List<String> scopes, ContextoPeticion contexto);

    ResultadoGestionCliente<SecretoEntregado> rotarSecreto(ActorUsuario actor, String clientId,
                                                          ContextoPeticion contexto);

    ResultadoGestionCliente<Void> cambiarEstado(ActorUsuario actor, String clientId, boolean activo,
                                               ContextoPeticion contexto);

    ResultadoGestionCliente<Void> conceder(ActorUsuario actor, String clientId, String scope,
                                          ContextoPeticion contexto);

    ResultadoGestionCliente<Void> revocar(ActorUsuario actor, String clientId, String scope,
                                         ContextoPeticion contexto);
}
