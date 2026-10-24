package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.CuentaIdentidad;
import com.marketplace.auth_service.identidad.application.dto.DocumentoIdentidad;
import com.marketplace.auth_service.identidad.application.dto.ResultadoConsultaIdentidad;
import com.marketplace.auth_service.identidad.application.dto.UsuarioServicioVista;
import com.marketplace.auth_service.identidad.application.port.in.ConsultaIdentidadPort;
import com.marketplace.auth_service.identidad.application.port.out.AuditoriaIdentidadPort;
import com.marketplace.auth_service.identidad.application.port.out.CuentasIdentidadPort;
import com.marketplace.auth_service.identidad.application.port.out.DocumentoIdentidadPort;
import com.marketplace.auth_service.identidad.application.port.out.RolesIdentidadPort;
import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.shared.domain.ScopeServicio;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta de identidad para módulos consumidores (SPEC-18 ESC-18.1, ESC-18.5,
 * ESC-18.6). Orden fijo: leer la cuenta, resolver roles, proyectar el documento
 * según el scope del actor (en claro o enmascarado, nunca ambos), y auditar el
 * acceso. Sin excepciones en la frontera exportada (igual que
 * {@code ResultadoTokenServicio} de SPEC-17); solo depende de sus propios
 * puertos y de {@code shared.domain}.
 */
public final class ConsultaIdentidadService implements ConsultaIdentidadPort {

    private final CuentasIdentidadPort cuentas;
    private final RolesIdentidadPort roles;
    private final DocumentoIdentidadPort documentos;
    private final AuditoriaIdentidadPort auditoria;
    private final Clock clock;

    public ConsultaIdentidadService(CuentasIdentidadPort cuentas, RolesIdentidadPort roles,
                                     DocumentoIdentidadPort documentos, AuditoriaIdentidadPort auditoria,
                                     Clock clock) {
        this.cuentas = Objects.requireNonNull(cuentas, "cuentas");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.documentos = Objects.requireNonNull(documentos, "documentos");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ResultadoConsultaIdentidad obtener(ActorServicio actor, String idTexto) {
        Objects.requireNonNull(actor, "actor");
        UUID id;
        try {
            id = UUID.fromString(Objects.requireNonNull(idTexto, "id").strip());
        } catch (IllegalArgumentException | NullPointerException e) {
            return new ResultadoConsultaIdentidad.Invalida("id", "No es un identificador válido.");
        }

        Optional<CuentaIdentidad> cuenta = cuentas.leer(id);
        if (cuenta.isEmpty()) {
            return new ResultadoConsultaIdentidad.NoEncontrado(id);
        }

        boolean verClaro = actor.tiene(ScopeServicio.USUARIOS_LEER_DOCUMENTO);
        Optional<DocumentoIdentidad> documento = verClaro ? documentos.leerEnClaro(id) : documentos.proyectar(id);
        auditar(actor, id, verClaro && documento.isPresent());

        CuentaIdentidad c = cuenta.get();
        String tipoDocumento = documento.map(DocumentoIdentidad::tipo).orElse(null);
        String documentoClaro = verClaro ? documento.map(DocumentoIdentidad::valor).orElse(null) : null;
        String documentoEnmascarado = verClaro ? null : documento.map(DocumentoIdentidad::valor).orElse(null);

        return new ResultadoConsultaIdentidad.Encontrado(new UsuarioServicioVista(c.id(), c.nombres(),
                c.apellidos(), c.nombreCompleto(), c.correo(), c.celular(), c.estado(), c.correoVerificado(),
                c.celularVerificado(), roles.rolesDe(id), tipoDocumento, documentoClaro, documentoEnmascarado));
    }

    /** No crítica siempre (RF-18.6); además crítica cuando el documento salió en claro (RF-18.4/ESC-18.5). */
    private void auditar(ActorServicio actor, UUID objetivo, boolean documentoEnClaro) {
        Instant ahora = clock.instant();
        if (documentoEnClaro) {
            auditoria.moduloObtuvoDocumento(actor.clientId(), objetivo, ahora);
        }
        auditoria.moduloConsultoUsuario(actor.clientId(), objetivo, ahora);
    }
}
