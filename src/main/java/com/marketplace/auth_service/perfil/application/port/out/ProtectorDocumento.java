package com.marketplace.auth_service.perfil.application.port.out;

import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoProtegido;

/**
 * Puerto de salida: descifra el documento con la clave externa indicada por keyId
 * (AES-256-GCM, envelope versionado). La implementación vive en un adaptador.
 */
public interface ProtectorDocumento {
    /**
     * @return el número en claro, solo para enmascararlo dentro de la proyección autorizada
     * @throws DocumentoNoLegibleException si la clave falta o la etiqueta/formato no son válidos
     */
    String descifrar(DocumentoProtegido documento);
}
