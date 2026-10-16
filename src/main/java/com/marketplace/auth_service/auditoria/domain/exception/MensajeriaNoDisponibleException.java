package com.marketplace.auth_service.auditoria.domain.exception;

/** Falta la clave de cifrado del outbox: el productor revierte en vez de guardar un correo en claro. */
public class MensajeriaNoDisponibleException extends RuntimeException {
    public MensajeriaNoDisponibleException() {
        super("Mensajería no disponible: falta la clave de cifrado del outbox");
    }
}
