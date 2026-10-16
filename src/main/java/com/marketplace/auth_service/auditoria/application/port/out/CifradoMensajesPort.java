package com.marketplace.auth_service.auditoria.application.port.out;

/** AES-256-GCM con clave externa e identificada; sin clave, no se guarda ningún correo. */
public interface CifradoMensajesPort {
    String cifrar(String textoPlano);

    String descifrar(String cifrado);
}
