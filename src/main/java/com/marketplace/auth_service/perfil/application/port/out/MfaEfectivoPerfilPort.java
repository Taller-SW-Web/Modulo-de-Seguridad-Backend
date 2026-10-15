package com.marketplace.auth_service.perfil.application.port.out;

import java.util.UUID;

/** Estado efectivo de MFA (bandera voluntaria o cualquier rol de gestión). No expone la bandera voluntaria. */
public interface MfaEfectivoPerfilPort {
    boolean efectivo(UUID usuarioId);
}
