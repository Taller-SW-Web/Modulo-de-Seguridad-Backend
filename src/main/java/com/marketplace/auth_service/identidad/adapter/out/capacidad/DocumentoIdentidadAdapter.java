package com.marketplace.auth_service.identidad.adapter.out.capacidad;

import com.marketplace.auth_service.identidad.application.dto.DocumentoIdentidad;
import com.marketplace.auth_service.identidad.application.port.out.DocumentoIdentidadPort;
import com.marketplace.auth_service.perfil.application.port.in.ConsultaDocumentoExportPort;
import java.util.Optional;
import java.util.UUID;

/** Puente a ConsultaDocumentoExportPort (Valery): documento enmascarado o en claro por scope. */
public final class DocumentoIdentidadAdapter implements DocumentoIdentidadPort {

    private final ConsultaDocumentoExportPort documentos;

    public DocumentoIdentidadAdapter(ConsultaDocumentoExportPort documentos) {
        this.documentos = documentos;
    }

    @Override
    public Optional<DocumentoIdentidad> proyectar(UUID usuarioId) {
        return documentos.proyectar(usuarioId).map(d -> new DocumentoIdentidad(d.tipo(), d.enmascarado()));
    }

    @Override
    public Optional<DocumentoIdentidad> leerEnClaro(UUID usuarioId) {
        return documentos.leerEnClaro(usuarioId).map(d -> new DocumentoIdentidad(d.tipo(), d.valor()));
    }
}
