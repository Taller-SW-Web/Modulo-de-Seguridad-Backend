package com.marketplace.auth_service.perfil.application.port.out;

import java.util.UUID;

/** Correos del cambio. El enlace solo viaja dentro del correo; nunca a logs, auditoría ni eventos. */
public interface NotificacionesCorreoPort {

    /** Emite el token de confirmación ligado a la dirección nueva y lo envía a esa dirección. */
    void enviarConfirmacion(UUID usuarioId, String correoNuevo);

    /** Avisa a la dirección anterior, que sigue operativa hasta la confirmación. */
    void avisarCorreoAnterior(String correoActual, String correoNuevo);
}
