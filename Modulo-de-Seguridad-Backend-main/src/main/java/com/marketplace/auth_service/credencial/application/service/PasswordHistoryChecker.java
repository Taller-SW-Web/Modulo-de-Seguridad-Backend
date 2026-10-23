package com.marketplace.auth_service.credencial.application.service;

import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.credencial.domain.model.Credencial;
import com.marketplace.auth_service.credencial.domain.model.PasswordHistorial;
import java.util.Objects;

public final class PasswordHistoryChecker {
    private final PasswordHasherPort passwordHasher;

    public PasswordHistoryChecker(PasswordHasherPort passwordHasher) {
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    }

    public boolean reutiliza(CharSequence secreto, Credencial credencial, PasswordHistorial historial) {
        Objects.requireNonNull(secreto, "secreto");
        Objects.requireNonNull(credencial, "credencial");
        Objects.requireNonNull(historial, "historial");

        String hashActual = credencial.hashActual().valor();
        return passwordHasher.verificar(secreto, credencial.hashActual())
                || historial.entradas().stream()
                        .filter(entrada -> !entrada.hash().valor().equals(hashActual))
                        .anyMatch(entrada -> passwordHasher.verificar(secreto, entrada.hash()));
    }
}
