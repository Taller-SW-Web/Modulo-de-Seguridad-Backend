package com.marketplace.auth_service.mfa.application.service;

import com.marketplace.auth_service.mfa.application.dto.RevisionCredencialMfa;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import com.marketplace.auth_service.mfa.domain.model.PoliticaMfa;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Consulta interna a MFA, no puerto exportado ni prueba de autenticación completa.
 * T003/T004 deberán revalidar bajo locks; localizar no consume ni autoriza una sesión.
 */
public final class ValidadorDesafioLogin {
    private final AlmacenDesafiosMfaPort desafios;
    private final CriptografiaDesafiosMfaPort criptografia;
    private final CuentasMfaPort cuentas;
    private final RolesMfaPort roles;
    private final RevisionCredencialMfaPort credenciales;
    private final Clock reloj;

    public ValidadorDesafioLogin(AlmacenDesafiosMfaPort desafios, CriptografiaDesafiosMfaPort criptografia,
                                CuentasMfaPort cuentas, RolesMfaPort roles,
                                RevisionCredencialMfaPort credenciales, Clock reloj) {
        this.desafios = Objects.requireNonNull(desafios);
        this.criptografia = Objects.requireNonNull(criptografia);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.roles = Objects.requireNonNull(roles);
        this.credenciales = Objects.requireNonNull(credenciales);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public Optional<DesafioMfa> localizarVigente(String token) {
        String hash;
        try {
            hash = criptografia.hashDesafio(token);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        var encontrado = desafios.buscarDesafioPorHash(hash);
        if (encontrado.isEmpty()) return Optional.empty();
        var desafio = encontrado.get();
        if (!desafio.hashToken().equals(hash) || !desafio.vigenteEn(reloj.instant())) return Optional.empty();
        var cuenta = cuentas.leer(desafio.usuarioId());
        if (cuenta.isEmpty() || !cuenta.get().usuarioId().equals(desafio.usuarioId())
                || !cuenta.get().disponible() || !cuenta.get().correoVerificado()) return Optional.empty();
        Set<String> actuales = Set.copyOf(roles.leer(desafio.usuarioId()));
        if (!PoliticaMfa.efectivo(cuenta.get().mfaVoluntario(), actuales)) return Optional.empty();
        var revision = new RevisionCredencialMfa(desafio.usuarioId(), desafio.credencialId(), desafio.credencialVersion());
        return credenciales.confirmar(revision) ? Optional.of(desafio) : Optional.empty();
    }
}
