package com.marketplace.auth_service.perfil.adapter.out.persistence.adapter;

import com.marketplace.auth_service.perfil.adapter.out.persistence.mapper.PerfilPersistenceMapper;
import com.marketplace.auth_service.perfil.adapter.out.persistence.repository.PerfilClienteJpaRepository;
import com.marketplace.auth_service.perfil.adapter.out.persistence.repository.PerfilVendedorJpaRepository;
import com.marketplace.auth_service.perfil.application.port.out.PerfilesPersistidosPort;
import com.marketplace.auth_service.perfil.domain.model.PerfilCliente;
import com.marketplace.auth_service.perfil.domain.model.PerfilVendedor;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Lectura de perfil_cliente / perfil_vendedor. Sin escritura: guardar el documento exige AES (pendiente). */
public class PerfilesPersistidosAdapter implements PerfilesPersistidosPort {
    private final PerfilClienteJpaRepository clientes;
    private final PerfilVendedorJpaRepository vendedores;

    public PerfilesPersistidosAdapter(PerfilClienteJpaRepository clientes, PerfilVendedorJpaRepository vendedores) {
        this.clientes = Objects.requireNonNull(clientes, "clientes");
        this.vendedores = Objects.requireNonNull(vendedores, "vendedores");
    }

    @Override
    public Optional<PerfilCliente> clienteDe(UUID usuarioId) {
        return clientes.findById(usuarioId).map(PerfilPersistenceMapper::aDominio);
    }

    @Override
    public Optional<PerfilVendedor> vendedorDe(UUID usuarioId) {
        return vendedores.findById(usuarioId).map(PerfilPersistenceMapper::aDominio);
    }
}
