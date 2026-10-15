package com.marketplace.auth_service.usuario.adapter.out.persistence.adapter;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.mapper.UsuarioPersistenceMapper;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.port.in.CambioBasicos;
import com.marketplace.auth_service.usuario.application.port.in.ContadoresProteccion;
import com.marketplace.auth_service.usuario.application.port.in.CuentaEscrituraInterna;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class CuentaEscrituraAdapter implements CuentaEscrituraInterna {

    private final UsuarioJpaRepository usuarioJpaRepository;

    public CuentaEscrituraAdapter(UsuarioJpaRepository usuarioJpaRepository) {
        this.usuarioJpaRepository = usuarioJpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CuentaSnapshot actualizarBasicos(UUID usuarioId, CambioBasicos cambio) {
        UsuarioJpaEntity entity = usuarioJpaRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));

        // Aplicar solo campos presentes (no null)
        if (cambio.nombres() != null) {
            entity.setNombres(cambio.nombres());
        }
        if (cambio.apellidos() != null) {
            entity.setApellidos(cambio.apellidos());
        }
        if (cambio.celular() != null) {
            entity.setCelular(cambio.celular());
        }
        if (cambio.tipoDocumento() != null) {
            entity.setTipoDocumento(cambio.tipoDocumento());
        }
        // numeroDocumento se maneja via Valery (encriptado/HMAC) - no aquí

        // NO tocar: roles, estado, passwordHash, contadores, MFA, fechas, versión

        UsuarioJpaEntity saved = usuarioJpaRepository.saveAndFlush(entity);
        return UsuarioPersistenceMapper.toSnapshot(saved, Set.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void guardarContadores(UUID usuarioId, ContadoresProteccion contadores) {
        UsuarioJpaEntity entity = usuarioJpaRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));

        entity.setBloqueosSeguidos(contadores.bloqueosSeguidos());
        entity.setIntentosFallidosConsecutivos(contadores.intentosFallidos());

        // NO tocar: básicos, roles, estado, password, MFA, fechas
        usuarioJpaRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void guardarMfaVoluntario(UUID usuarioId, boolean habilitado) {
        // Reservado para SPEC-10 - no implementado en H3
        throw new UnsupportedOperationException("guardarMfaVoluntario reservado para SPEC-10");
    }
}