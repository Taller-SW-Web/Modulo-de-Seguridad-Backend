package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.mfa.application.dto.RevisionCredencialMfa;

/** El puente futuro confirma usuario/id/version mediante CredencialesPort de Juan José. */
public interface RevisionCredencialMfaPort {
    boolean confirmar(RevisionCredencialMfa revision);
}
