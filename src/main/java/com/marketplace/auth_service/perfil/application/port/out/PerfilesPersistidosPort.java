package com.marketplace.auth_service.perfil.application.port.out;

import com.marketplace.auth_service.perfil.domain.model.PerfilCliente;
import com.marketplace.auth_service.perfil.domain.model.PerfilVendedor;
import java.util.Optional;
import java.util.UUID;

/** Persistencia propia de perfil_cliente / perfil_vendedor (el adaptador JPA llega con T001 completo). */
public interface PerfilesPersistidosPort {
    Optional<PerfilCliente> clienteDe(UUID usuarioId);

    Optional<PerfilVendedor> vendedorDe(UUID usuarioId);
}
