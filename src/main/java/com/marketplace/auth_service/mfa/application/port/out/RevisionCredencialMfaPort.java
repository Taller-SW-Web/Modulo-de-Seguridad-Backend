package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.mfa.application.dto.RevisionCredencialMfa;

/** Confirma usuario/id/version actuales mediante CredencialesPort, bajo la transacción del caller. */
public interface RevisionCredencialMfaPort {
    boolean confirmar(RevisionCredencialMfa revision);
}
