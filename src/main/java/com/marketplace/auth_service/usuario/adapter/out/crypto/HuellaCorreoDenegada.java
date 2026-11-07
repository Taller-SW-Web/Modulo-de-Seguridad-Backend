package com.marketplace.auth_service.usuario.adapter.out.crypto;

import com.marketplace.auth_service.usuario.application.port.out.HuellaCorreoPort;
import com.marketplace.auth_service.usuario.domain.exception.CuotaNoDisponibleException;

/** Sin clave HMAC válida el reenvío falla cerrado (503) para cualquier correo, sin revelar cuentas. */
public final class HuellaCorreoDenegada implements HuellaCorreoPort {
    @Override public String generar(String correoNormalizado) { throw new CuotaNoDisponibleException(); }
}
