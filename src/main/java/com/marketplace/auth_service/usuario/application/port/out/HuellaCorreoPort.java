package com.marketplace.auth_service.usuario.application.port.out;

/** Seudónimo del correo para la cuota: se guarda la huella, nunca el correo (Ley 29733). */
public interface HuellaCorreoPort {
    /** @throws com.marketplace.auth_service.usuario.domain.exception.CuotaNoDisponibleException sin clave */
    String generar(String correoNormalizado);
}
