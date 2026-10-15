package com.marketplace.auth_service.mfa.adapter.out.capacidad;

import com.marketplace.auth_service.mfa.application.dto.ContactoMfa;
import com.marketplace.auth_service.mfa.application.dto.CuentaMfaSnapshot;
import com.marketplace.auth_service.mfa.application.port.out.CuentasMfaPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.Optional;
import java.util.UUID;

/** Consume la identidad y el estado efectivo sin consultar tablas ajenas. */
public final class CuentasMfaAdapter implements CuentasMfaPort {
    private final CuentasPort cuentas;
    public CuentasMfaAdapter(CuentasPort cuentas) { this.cuentas = cuentas; }

    @Override public Optional<CuentaMfaSnapshot> leer(UUID usuarioId) {
        return cuentas.leer(usuarioId).map(c -> new CuentaMfaSnapshot(c.id(),
                "ACTIVO".equals(c.estadoBase()) && "ACTIVO".equals(c.estadoEfectivo()),
                c.correoVerificado(), c.mfaHabilitado()));
    }

    @Override public void bloquear(UUID usuarioId) { cuentas.bloquear(usuarioId); }

    @Override public Optional<ContactoMfa> contacto(UUID usuarioId) {
        return cuentas.leer(usuarioId).map(c -> new ContactoMfa(c.correo(), c.celular()));
    }
}
