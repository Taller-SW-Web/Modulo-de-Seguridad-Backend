package com.marketplace.auth_service.usuario.application.dto;

/**
 * Resultado de aplicar un cambio de correo confirmado (SPEC-16, ESC-16.3). {@code CORREO_NO_DISPONIBLE}
 * cubre la dirección que otra cuenta tomó entre la solicitud y la confirmación.
 */
public enum ResultadoAplicacionCorreo { APLICADO, CORREO_NO_DISPONIBLE, CUENTA_NO_ENCONTRADA }
