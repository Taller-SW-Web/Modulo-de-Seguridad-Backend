package com.marketplace.auth_service.perfil.adapter.out.persistence.adapter;

import com.marketplace.auth_service.perfil.adapter.out.persistence.entity.PerfilClienteJpaEntity;
import com.marketplace.auth_service.perfil.adapter.out.persistence.entity.PerfilVendedorJpaEntity;
import com.marketplace.auth_service.perfil.adapter.out.persistence.mapper.PerfilPersistenceMapper;
import com.marketplace.auth_service.perfil.adapter.out.persistence.repository.PerfilClienteJpaRepository;
import com.marketplace.auth_service.perfil.adapter.out.persistence.repository.PerfilVendedorJpaRepository;
import com.marketplace.auth_service.perfil.application.port.out.EscrituraPerfilesPort;
import com.marketplace.auth_service.perfil.application.port.out.PerfilesPersistidosPort;
import com.marketplace.auth_service.perfil.domain.model.PerfilCliente;
import com.marketplace.auth_service.perfil.domain.model.PerfilVendedor;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura y escritura de perfil_cliente / perfil_vendedor. Escribe fecha de nacimiento y atributos de
 * vendedor (FEAT-04-T6); el documento no se escribe: guardarlo exige AES (pendiente).
 */
public class PerfilesPersistidosAdapter implements PerfilesPersistidosPort, EscrituraPerfilesPort {
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

    /** Crea la fila sin documento si falta; las columnas del documento no se tocan. */
    @Override
    public void guardarFechaNacimiento(UUID usuarioId, LocalDate fechaNacimiento) {
        PerfilClienteJpaEntity fila = clientes.findById(usuarioId).orElseGet(() -> {
            PerfilClienteJpaEntity nueva = new PerfilClienteJpaEntity();
            nueva.setUsuarioId(usuarioId);
            return nueva;
        });
        fila.setFechaNacimiento(fechaNacimiento);
        clientes.save(fila);
    }

    @Override
    public void guardarVendedor(UUID usuarioId, String codigoVendedor, String tienda, LocalDate fechaIngreso) {
        PerfilVendedorJpaEntity fila = vendedores.findById(usuarioId).orElseGet(() -> {
            PerfilVendedorJpaEntity nueva = new PerfilVendedorJpaEntity();
            nueva.setUsuarioId(usuarioId);
            return nueva;
        });
        fila.setCodigoVendedor(codigoVendedor);
        fila.setTienda(tienda);
        fila.setFechaIngreso(fechaIngreso);
        vendedores.save(fila);
    }
}
