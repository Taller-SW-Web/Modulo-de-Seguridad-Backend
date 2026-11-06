package com.marketplace.auth_service.usuario.adapter.out.persistence.adapter;

import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAplicacionCorreo;
import com.marketplace.auth_service.usuario.application.port.out.CuentasVerificacionPort;
import com.marketplace.auth_service.usuario.domain.model.CuentaPendiente;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Activación por verificación de correo (RF-02.2) y cambio de correo confirmado (RF-16.6), con la fila de la cuenta bloqueada. */
@Transactional(propagation = Propagation.MANDATORY)
public class CuentasVerificacionJpaAdapter implements CuentasVerificacionPort {
    private final UsuarioJpaRepository usuarios;

    public CuentasVerificacionJpaAdapter(UsuarioJpaRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public boolean activarSiPendiente(UUID usuarioId, Instant ahora) {
        var cuenta = usuarios.lockById(usuarioId).orElse(null);
        if (cuenta == null || cuenta.getEstado() != EstadoCuenta.PENDIENTE_VERIFICACION) {
            return false;
        }
        cuenta.setEstado(EstadoCuenta.ACTIVO);
        cuenta.setCorreoVerificado(true);
        cuenta.setActualizadoEn(ahora.atOffset(ZoneOffset.UTC));
        usuarios.saveAndFlush(cuenta);
        return true;
    }

    @Override
    public ResultadoAplicacionCorreo aplicarCambioCorreo(UUID usuarioId, String correoNuevo, Instant ahora) {
        var cuenta = usuarios.lockById(usuarioId).orElse(null);
        if (cuenta == null) {
            return ResultadoAplicacionCorreo.CUENTA_NO_ENCONTRADA;
        }
        String normalizado = correoNuevo.strip().toLowerCase(Locale.ROOT);
        // Con la cuenta bloqueada: otra cuenta pudo tomar la dirección (o ser la misma) desde la solicitud.
        if (usuarios.findByCorreo(normalizado).isPresent()) {
            return ResultadoAplicacionCorreo.CORREO_NO_DISPONIBLE;
        }
        cuenta.setCorreo(normalizado);
        cuenta.setCorreoVerificado(true);
        cuenta.setActualizadoEn(ahora.atOffset(ZoneOffset.UTC));
        try {
            usuarios.saveAndFlush(cuenta);
        } catch (DataIntegrityViolationException carrera) {
            // Otra confirmación igual ganó entre la lectura y el flush: uq_usuario_correo_lower lo impide.
            return ResultadoAplicacionCorreo.CORREO_NO_DISPONIBLE;
        }
        return ResultadoAplicacionCorreo.APLICADO;
    }

    @Override
    public Optional<CuentaPendiente> buscarPendiente(String correoNormalizado) {
        return usuarios.findByCorreo(correoNormalizado)
                .filter(cuenta -> cuenta.getEstado() == EstadoCuenta.PENDIENTE_VERIFICACION)
                .map(cuenta -> new CuentaPendiente(cuenta.getId(), cuenta.getCorreo(), cuenta.getNombres(),
                        cuenta.getCanalOrigen()));
    }
}
