package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.CuentaIdentidad;
import com.marketplace.auth_service.identidad.application.dto.DocumentoIdentidad;
import com.marketplace.auth_service.identidad.application.dto.ResultadoConsultaIdentidad;
import com.marketplace.auth_service.identidad.application.dto.ResultadoConsultaLoteIdentidad;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    /** SPEC-18 RF-18.2/ESC-18.3: más de 100 identificadores es 400, nunca un truncado en silencio. */
    static final int MAXIMO_LOTE = 100;

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
        auditar(actor, id, verClaro && documento.isPresent(), clock.instant());

        return new ResultadoConsultaIdentidad.Encontrado(vista(cuenta.get(), roles.rolesDe(id), documento, verClaro));
    }

    /**
     * Lote de hasta 100 identificadores (SPEC-18 RF-18.2/ESC-18.2/ESC-18.3, FEAT-06-T2).
     * Orden fijo: validar tamaño y formato antes de leer nada, colapsar duplicados,
     * leer las cuentas encontradas en una sola llamada de lote y auditar solo esas —
     * igual que {@link #obtener}, un id que no existe no genera auditoría.
     */
    @Override
    public ResultadoConsultaLoteIdentidad obtenerLote(ActorServicio actor, List<String> idsTexto) {
        Objects.requireNonNull(actor, "actor");
        List<String> textos = idsTexto == null ? List.of() : idsTexto;
        if (textos.isEmpty()) {
            return new ResultadoConsultaLoteIdentidad.Invalida("ids", "Debe incluir al menos un identificador.");
        }
        if (textos.size() > MAXIMO_LOTE) {
            return new ResultadoConsultaLoteIdentidad.LoteDemasiadoGrande(textos.size(), MAXIMO_LOTE);
        }

        Set<UUID> idsUnicos = new LinkedHashSet<>();
        for (int i = 0; i < textos.size(); i++) {
            UUID id;
            try {
                id = UUID.fromString(Objects.requireNonNull(textos.get(i), "id").strip());
            } catch (IllegalArgumentException | NullPointerException e) {
                return new ResultadoConsultaLoteIdentidad.Invalida("ids[" + i + "]", "No es un identificador válido.");
            }
            idsUnicos.add(id);
        }

        boolean verClaro = actor.tiene(ScopeServicio.USUARIOS_LEER_DOCUMENTO);
        Map<UUID, CuentaIdentidad> encontradas = cuentas.leerLote(idsUnicos);
        Instant ahora = clock.instant();

        List<UsuarioServicioVista> usuarios = new ArrayList<>();
        List<UUID> noEncontrados = new ArrayList<>();
        for (UUID id : idsUnicos) {
            CuentaIdentidad cuenta = encontradas.get(id);
            if (cuenta == null) {
                noEncontrados.add(id);
                continue;
            }
            Optional<DocumentoIdentidad> documento = verClaro ? documentos.leerEnClaro(id) : documentos.proyectar(id);
            usuarios.add(vista(cuenta, roles.rolesDe(id), documento, verClaro));
            auditar(actor, id, verClaro && documento.isPresent(), ahora);
        }
        return new ResultadoConsultaLoteIdentidad.Resultado(usuarios, noEncontrados);
    }

    private static UsuarioServicioVista vista(CuentaIdentidad c, List<String> rolesDe,
                                               Optional<DocumentoIdentidad> documento, boolean verClaro) {
        String tipoDocumento = documento.map(DocumentoIdentidad::tipo).orElse(null);
        String documentoClaro = verClaro ? documento.map(DocumentoIdentidad::valor).orElse(null) : null;
        String documentoEnmascarado = verClaro ? null : documento.map(DocumentoIdentidad::valor).orElse(null);
        return new UsuarioServicioVista(c.id(), c.nombres(), c.apellidos(), c.nombreCompleto(), c.correo(),
                c.celular(), c.estado(), c.correoVerificado(), c.celularVerificado(), rolesDe, tipoDocumento,
                documentoClaro, documentoEnmascarado);
    }

    /** No crítica siempre (RF-18.6); además crítica cuando el documento salió en claro (RF-18.4/ESC-18.5). */
    private void auditar(ActorServicio actor, UUID objetivo, boolean documentoEnClaro, Instant ahora) {
        if (documentoEnClaro) {
            auditoria.moduloObtuvoDocumento(actor.clientId(), objetivo, ahora);
        }
        auditoria.moduloConsultoUsuario(actor.clientId(), objetivo, ahora);
    }
}
