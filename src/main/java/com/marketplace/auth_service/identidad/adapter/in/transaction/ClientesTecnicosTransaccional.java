package com.marketplace.auth_service.identidad.adapter.in.transaction;

import com.marketplace.auth_service.identidad.application.dto.ClienteTecnico;
import com.marketplace.auth_service.identidad.application.dto.ResultadoGestionCliente;
import com.marketplace.auth_service.identidad.application.dto.ScopeDisponible;
import com.marketplace.auth_service.identidad.application.dto.SecretoEntregado;
import com.marketplace.auth_service.identidad.application.port.in.ClientesTecnicosPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Frontera transaccional de la gestión de clientes técnicos (SPEC-19, RF-19.14):
 * cada escritura y su auditoría crítica se confirman o revierten juntas.
 */
public class ClientesTecnicosTransaccional implements ClientesTecnicosPort {

    private final ClientesTecnicosPort delegado;

    public ClientesTecnicosTransaccional(ClientesTecnicosPort delegado) {
        this.delegado = Objects.requireNonNull(delegado, "delegado");
    }

    @Override
    @Transactional(readOnly = true)
    public ResultadoGestionCliente<List<ClienteTecnico>> listar(ActorUsuario actor, ContextoPeticion contexto) {
        return delegado.listar(actor, contexto);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultadoGestionCliente<List<ScopeDisponible>> catalogo(ActorUsuario actor, ContextoPeticion contexto) {
        return delegado.catalogo(actor, contexto);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoGestionCliente<SecretoEntregado> crear(ActorUsuario actor, String clientId, String nombre,
                                                          List<String> scopes, ContextoPeticion contexto) {
        return delegado.crear(actor, clientId, nombre, scopes, contexto);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoGestionCliente<SecretoEntregado> rotarSecreto(ActorUsuario actor, String clientId,
                                                                 ContextoPeticion contexto) {
        return delegado.rotarSecreto(actor, clientId, contexto);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoGestionCliente<Void> cambiarEstado(ActorUsuario actor, String clientId, boolean activo,
                                                      ContextoPeticion contexto) {
        return delegado.cambiarEstado(actor, clientId, activo, contexto);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoGestionCliente<Void> conceder(ActorUsuario actor, String clientId, String scope,
                                                 ContextoPeticion contexto) {
        return delegado.conceder(actor, clientId, scope, contexto);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoGestionCliente<Void> revocar(ActorUsuario actor, String clientId, String scope,
                                                ContextoPeticion contexto) {
        return delegado.revocar(actor, clientId, scope, contexto);
    }
}
