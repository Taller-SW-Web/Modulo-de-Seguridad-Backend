package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.DocumentoProyectado;
import com.marketplace.auth_service.perfil.application.port.out.ProtectorDocumento;
import com.marketplace.auth_service.perfil.domain.model.DocumentoEnmascarado;
import com.marketplace.auth_service.perfil.domain.model.DocumentoProtegido;
import java.util.Objects;
import java.util.Optional;

/**
 * Proyección segura del documento: descifra y enmascara. Si no hay documento
 * devuelve vacío; si hay y no se puede leer, propaga el fallo (no finge ausencia).
 */
public final class ProyectorDocumento {
    private final ProtectorDocumento protector;

    public ProyectorDocumento(ProtectorDocumento protector) {
        this.protector = Objects.requireNonNull(protector, "protector");
    }

    public Optional<DocumentoProyectado> proyectar(Optional<DocumentoProtegido> documento) {
        return documento.map(protegido -> {
            String enClaro = protector.descifrar(protegido);
            return new DocumentoProyectado(protegido.tipo().name(), DocumentoEnmascarado.de(enClaro));
        });
    }
}
