package com.marketplace.auth_service.usuario.adapter.out.persistence.adapter;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.dto.CuentaPrueba;
import com.marketplace.auth_service.usuario.application.port.in.AltaCuentaPruebaInterna;
import com.marketplace.auth_service.usuario.domain.model.CanalOrigen;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserta la identidad de una cuenta de prueba: ACTIVO, correo verificado y sin
 * contadores. No marca aceptación de términos (H-12): una carga técnica no es un
 * consentimiento.
 */
@Transactional(propagation = Propagation.MANDATORY)
public class AltaCuentaPruebaAdapter implements AltaCuentaPruebaInterna {
    private final UsuarioJpaRepository usuarios;
    private final Clock clock;

    public AltaCuentaPruebaAdapter(UsuarioJpaRepository usuarios, Clock clock) {
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Override
    public boolean asegurar(CuentaPrueba cuenta) {
        if (usuarios.existsById(cuenta.id())) {
            return false;
        }
        if (usuarios.findByCorreo(cuenta.correo()).isPresent()) {
            throw new IllegalStateException("El correo de la cuenta de prueba " + cuenta.id()
                    + " ya pertenece a otra cuenta");
        }
        var entity = new UsuarioJpaEntity();
        entity.setId(cuenta.id());
        entity.setCorreo(cuenta.correo());
        entity.setNombres(cuenta.nombres());
        entity.setApellidos(cuenta.apellidos());
        entity.setEstado(EstadoCuenta.ACTIVO);
        entity.setCorreoVerificado(true);
        entity.setCelularVerificado(false);
        entity.setMfaHabilitado(cuenta.mfaVoluntario());
        entity.setAceptaTerminos(false);
        entity.setCanalOrigen(CanalOrigen.WEB);
        entity.setCreadoEn(OffsetDateTime.now(clock));
        usuarios.saveAndFlush(entity);
        return true;
    }
}
