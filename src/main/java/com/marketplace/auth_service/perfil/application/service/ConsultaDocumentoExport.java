package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.DocumentoProyectado;
import com.marketplace.auth_service.perfil.application.port.in.ConsultaDocumentoExportPort;
import com.marketplace.auth_service.perfil.application.port.out.PerfilesPersistidosPort;
import com.marketplace.auth_service.perfil.application.port.out.ProtectorDocumento;
import com.marketplace.auth_service.perfil.domain.model.DocumentoProtegido;
import com.marketplace.auth_service.perfil.domain.model.PerfilCliente;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura del documento para consumidores externos (SPEC-18): reutiliza el
 * mismo descifrado de {@link ProyectorDocumento} para el caso enmascarado, y
 * añade la salida en claro solo para el caso autorizado por scope. Sin
 * Spring, JPA ni HTTP.
 */
public final class ConsultaDocumentoExport implements ConsultaDocumentoExportPort {

    private final PerfilesPersistidosPort perfiles;
    private final ProtectorDocumento protector;
    private final ProyectorDocumento proyector;

    public ConsultaDocumentoExport(PerfilesPersistidosPort perfiles, ProtectorDocumento protector) {
        this.perfiles = Objects.requireNonNull(perfiles, "perfiles");
        this.protector = Objects.requireNonNull(protector, "protector");
        this.proyector = new ProyectorDocumento(protector);
    }

    @Override
    public Optional<DocumentoProyectado> proyectar(UUID usuarioId) {
        return proyector.proyectar(documentoDe(usuarioId));
    }

    @Override
    public Optional<String> leerEnClaro(UUID usuarioId) {
        return documentoDe(usuarioId).map(protector::descifrar);
    }

    private Optional<DocumentoProtegido> documentoDe(UUID usuarioId) {
        return perfiles.clienteDe(usuarioId).flatMap(PerfilCliente::documento);
    }
}
