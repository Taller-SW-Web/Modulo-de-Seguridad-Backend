package com.marketplace.auth_service.auditoria.adapter.out.crypto;

import com.marketplace.auth_service.auditoria.application.port.out.CifradoMensajesPort;
import com.marketplace.auth_service.auditoria.domain.exception.MensajeriaNoDisponibleException;

/** Sin AUTH_OUTBOX_AES_KEY: ningún correo se guarda en claro; su productor revierte. */
public final class CifradoMensajesDenegado implements CifradoMensajesPort {
    @Override public String cifrar(String textoPlano) { throw new MensajeriaNoDisponibleException(); }

    @Override public String descifrar(String cifrado) { throw new MensajeriaNoDisponibleException(); }
}
