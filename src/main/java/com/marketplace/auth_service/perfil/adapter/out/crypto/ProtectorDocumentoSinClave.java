package com.marketplace.auth_service.perfil.adapter.out.crypto;

import com.marketplace.auth_service.perfil.application.port.out.ProtectorDocumento;
import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoProtegido;

/**
 * Protector mientras no exista la clave AES-256-GCM externa ni su
 * {@code documento_key_id} (TEC-INFRA, Christian). Falla cerrado: un documento
 * cifrado no se descifra ni se finge ausente, y la consulta de ese perfil falla.
 * Las cuentas sin documento se proyectan con normalidad.
 */
public final class ProtectorDocumentoSinClave implements ProtectorDocumento {

    @Override
    public String descifrar(DocumentoProtegido documento) {
        throw new DocumentoNoLegibleException("No hay clave configurada para el documento");
    }
}
