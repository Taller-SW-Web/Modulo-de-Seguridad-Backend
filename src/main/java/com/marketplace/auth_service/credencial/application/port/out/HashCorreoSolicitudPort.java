package com.marketplace.auth_service.credencial.application.port.out;

/** HMAC estable del correo normalizado para cuota sin conservar PII en solicitud_limitada. */
public interface HashCorreoSolicitudPort {
    String generar(String correoNormalizado);
}
