package com.marketplace.auth_service.mfa.adapter.out.capacidad;

import com.marketplace.auth_service.credencial.application.dto.RevisionCredencial;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.mfa.application.dto.RevisionCredencialMfa;
import com.marketplace.auth_service.mfa.application.port.out.RevisionCredencialMfaPort;

/** La revisión conserva usuario_id/version reales; no introduce otro identificador. */
public final class RevisionCredencialMfaAdapter implements RevisionCredencialMfaPort {
    private final CredencialesPort credenciales;
    public RevisionCredencialMfaAdapter(CredencialesPort credenciales) { this.credenciales = credenciales; }
    @Override public boolean confirmar(RevisionCredencialMfa r) {
        return credenciales.confirmarRevision(new RevisionCredencial(r.usuarioId(), r.credencialId(), r.version()));
    }
}
