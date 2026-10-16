package com.marketplace.auth_service.usuario.adapter.out.persistence.adapter;

import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.port.in.CambioBasicos;
import com.marketplace.auth_service.usuario.application.port.in.ContadoresProteccion;
import com.marketplace.auth_service.usuario.application.port.in.CuentaEscrituraInterna;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

@Transactional(propagation = Propagation.MANDATORY)
public class CuentaEscrituraAdapter implements CuentaEscrituraInterna {
    private final UsuarioJpaRepository usuarios;
    private final UsuarioRepositoryPort consultas;
    public CuentaEscrituraAdapter(UsuarioJpaRepository usuarios, UsuarioRepositoryPort consultas) {
        this.usuarios = usuarios;
        this.consultas = consultas;
    }
    @Override
    public CuentaSnapshot actualizarBasicos(UUID id, CambioBasicos cambio) {
        var entity = usuarios.findById(id).orElseThrow(() -> new IllegalArgumentException("Cuenta inexistente"));
        if (cambio.nombres().presente()) entity.setNombres(cambio.nombres().valor());
        if (cambio.apellidos().presente()) entity.setApellidos(cambio.apellidos().valor());
        if (cambio.celular().presente() && !Objects.equals(entity.getCelular(), cambio.celular().valor())) {
            entity.setCelular(cambio.celular().valor()); entity.setCelularVerificado(false);
        }
        usuarios.saveAndFlush(entity);
        return consultas.findById(id).orElseThrow();
    }
    @Override
    public void guardarContadores(UUID id, ContadoresProteccion contadores) {
        var entity = usuarios.findById(id).orElseThrow(() -> new IllegalArgumentException("Cuenta inexistente"));
        entity.setBloqueosSeguidos(contadores.bloqueosSeguidos());
        entity.setIntentosFallidosConsecutivos(contadores.intentosFallidos());
        usuarios.saveAndFlush(entity);
    }
    @Override
    public void guardarMfaVoluntario(UUID id, boolean habilitado) {
        throw new UnsupportedOperationException("guardarMfaVoluntario reservado para SPEC-10");
    }
}
