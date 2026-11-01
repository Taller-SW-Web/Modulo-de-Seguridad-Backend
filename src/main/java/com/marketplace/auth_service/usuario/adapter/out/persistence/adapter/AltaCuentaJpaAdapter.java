package com.marketplace.auth_service.usuario.adapter.out.persistence.adapter;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.port.out.AltaCuentaPort;
import com.marketplace.auth_service.usuario.domain.model.CuentaNueva;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.time.ZoneOffset;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserta la cuenta del registro público: {@code PENDIENTE_VERIFICACION}, correo sin
 * verificar y términos aceptados con fecha y versión (ck_usuario_terminos).
 */
@Transactional(propagation = Propagation.MANDATORY)
public class AltaCuentaJpaAdapter implements AltaCuentaPort {
    private final UsuarioJpaRepository usuarios;

    public AltaCuentaJpaAdapter(UsuarioJpaRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public boolean crear(CuentaNueva cuenta) {
        if (usuarios.findByCorreo(cuenta.correo()).isPresent()) {
            return false;
        }
        var entity = new UsuarioJpaEntity();
        entity.setId(cuenta.id());
        entity.setCorreo(cuenta.correo());
        entity.setNombres(cuenta.nombres());
        entity.setApellidos(cuenta.apellidos());
        entity.setCelular(cuenta.celular());
        entity.setEstado(EstadoCuenta.PENDIENTE_VERIFICACION);
        entity.setCorreoVerificado(false);
        entity.setCelularVerificado(false);
        entity.setMfaHabilitado(false);
        entity.setAceptaTerminos(true);
        entity.setFechaAceptacion(cuenta.aceptadoEn().atOffset(ZoneOffset.UTC));
        entity.setVersionTerminos(cuenta.versionTerminos());
        entity.setCanalOrigen(cuenta.canalOrigen());
        entity.setCreadoEn(cuenta.aceptadoEn().atOffset(ZoneOffset.UTC));
        try {
            usuarios.saveAndFlush(entity);
        } catch (DataIntegrityViolationException correoConcurrente) {
            // uq_usuario_correo_lower: otro registro ganó la carrera; la transacción ya no sirve y se revierte.
            return false;
        }
        return true;
    }
}
