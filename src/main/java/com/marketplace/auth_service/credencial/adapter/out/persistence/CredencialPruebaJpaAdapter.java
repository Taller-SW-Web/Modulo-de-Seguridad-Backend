package com.marketplace.auth_service.credencial.adapter.out.persistence;

import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.CredencialJpaEntity;
import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.PasswordHistorialJpaEntity;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.CredencialJpaRepository;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.PasswordHistorialJpaRepository;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CredencialPruebaInterna;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Usa el mismo hasher que la verificación del login, así el hash es compatible por construcción. */
@Transactional(propagation = Propagation.MANDATORY)
public class CredencialPruebaJpaAdapter implements CredencialPruebaInterna {
    private final CredencialJpaRepository credenciales;
    private final PasswordHistorialJpaRepository historiales;
    private final PasswordHasherPort hasher;
    private final Clock clock;

    public CredencialPruebaJpaAdapter(CredencialJpaRepository credenciales, PasswordHistorialJpaRepository historiales,
                                      PasswordHasherPort hasher, Clock clock) {
        this.credenciales = credenciales;
        this.historiales = historiales;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Override
    public boolean asegurar(UUID usuarioId, SecretoEfimero secreto) {
        if (credenciales.existsById(usuarioId)) {
            return false;
        }
        String hash = hasher.generar(secreto.valor()).valor();
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        credenciales.saveAndFlush(new CredencialJpaEntity(usuarioId, hash, ahora));
        historiales.saveAndFlush(new PasswordHistorialJpaEntity(UUID.randomUUID(), usuarioId, hash, ahora));
        return true;
    }
}
