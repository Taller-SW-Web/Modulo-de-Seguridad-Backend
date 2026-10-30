package com.marketplace.auth_service.perfil.application.dto;

/** Resultado de pedir el cambio de correo; el controlador lo traduce a 202, 401 o 409. */
public enum ResultadoSolicitudCorreo {
    /** Enlace enviado a la dirección nueva y aviso a la anterior. */
    ENVIADA,
    /** La contraseña actual no coincide (401 CREDENCIALES_INVALIDAS). */
    CONTRASENA_INCORRECTA,
    /** La dirección ya pertenece a una cuenta, o es la misma que la actual (409 CORREO_NO_DISPONIBLE). */
    CORREO_NO_DISPONIBLE
}
