package com.marketplace.auth_service.usuario.adapter.out.persistence.adapter;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.port.out.CuentasPersonalPort;
import com.marketplace.auth_service.usuario.domain.model.CanalOrigen;
import com.marketplace.auth_service.usuario.domain.model.CuentaPersonalNueva;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuentas de personal (SPEC-03): nacen {@code PENDIENTE_VERIFICACION}, con el correo sin verificar y
 * {@code acepta_terminos = false} (ck_usuario_terminos admite fecha y versión nulas). Esa bandera es lo
 * que distingue una cuenta creada por otra persona de un registro público pendiente, que siempre nace
 * con los términos aceptados.
 */
@Transactional(propagation = Propagation.MANDATORY)
public class CuentasPersonalJpaAdapter implements CuentasPersonalPort {
    private final UsuarioJpaRepository usuarios;

    public CuentasPersonalJpaAdapter(UsuarioJpaRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public boolean crear(CuentaPersonalNueva cuenta) {
        if (usuarios.findByCorreo(cuenta.correo()).isPresent()) {
            return false;
        }
        var entity = new UsuarioJpaEntity();
        entity.setId(cuenta.id());
        entity.setCorreo(cuenta.correo());
        entity.setNombres(cuenta.nombres());
        entity.setApellidos(cuenta.apellidos());
        entity.setEstado(EstadoCuenta.PENDIENTE_VERIFICACION);
        entity.setCorreoVerificado(false);
        entity.setCelularVerificado(false);
        entity.setMfaHabilitado(false);
        entity.setAceptaTerminos(false);
        // El canal describe por dónde se registró un cliente; el personal lo crea el panel (WEB).
        entity.setCanalOrigen(CanalOrigen.WEB);
        entity.setCreadoEn(cuenta.creadaEn().atOffset(ZoneOffset.UTC));
        try {
            usuarios.saveAndFlush(entity);
        } catch (DataIntegrityViolationException correoConcurrente) {
            // uq_usuario_correo_lower: otra alta ganó la carrera; la transacción ya no sirve y se revierte.
            return false;
        }
        return true;
    }

    @Override
    public boolean bloquearSinActivar(UUID usuarioId) {
        return usuarios.lockById(usuarioId)
                .filter(cuenta -> cuenta.getEstado() == EstadoCuenta.PENDIENTE_VERIFICACION)
                .filter(cuenta -> !cuenta.isAceptaTerminos())
                .isPresent();
    }

    @Override
    public void activar(UUID usuarioId, String versionTerminos, Instant ahora) {
        var cuenta = usuarios.lockById(usuarioId).orElseThrow();
        var momento = ahora.atOffset(ZoneOffset.UTC);
        cuenta.setEstado(EstadoCuenta.ACTIVO);
        cuenta.setCorreoVerificado(true);
        cuenta.setAceptaTerminos(true);
        cuenta.setFechaAceptacion(momento);
        cuenta.setVersionTerminos(versionTerminos);
        cuenta.setActualizadoEn(momento);
        usuarios.saveAndFlush(cuenta);
    }
}
