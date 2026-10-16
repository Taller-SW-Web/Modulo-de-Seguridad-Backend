package com.marketplace.auth_service.auditoria.application.service;

import com.marketplace.auth_service.auditoria.application.dto.PaginaActividad;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.out.AuditoriaPersistenciaPort;
import com.marketplace.auth_service.auditoria.application.port.out.ReintentoAuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.out.TareasTrasCommitPort;
import com.marketplace.auth_service.auditoria.domain.exception.RegistroDiferidoNoDisponibleException;
import com.marketplace.auth_service.auditoria.domain.model.ActorTipo;
import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import com.marketplace.auth_service.auditoria.domain.model.Resultado;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro central de seguridad (EP-06-T002/T003/T004, SPEC-12).
 *
 * <ul>
 *   <li>Crítica: se valida (catálogo cerrado y detalle sin secretos) y se guarda en la
 *       transacción del productor; si falla, el productor revierte (RF-12.6).</li>
 *   <li>No crítica: se valida ya (un error de formato es del productor) y se guarda después
 *       del commit, deduplicada por {@code registroId}; su fallo nunca revierte el hecho.</li>
 *   <li>Actividad propia: solo registros cuyo objetivo es el titular, fecha descendente.</li>
 * </ul>
 */
public final class AuditoriaService implements AuditoriaPort {

    static final int TAMANO_MAXIMO = 100;

    private final AuditoriaPersistenciaPort persistencia;
    private final TareasTrasCommitPort trasCommit;
    private final ReintentoAuditoriaPort reintentos;

    public AuditoriaService(AuditoriaPersistenciaPort persistencia, TareasTrasCommitPort trasCommit) {
        this(persistencia, trasCommit, registro -> {
            throw new RegistroDiferidoNoDisponibleException(
                    new IllegalStateException("sin cola de reintento configurada"));
        });
    }

    public AuditoriaService(AuditoriaPersistenciaPort persistencia, TareasTrasCommitPort trasCommit,
                            ReintentoAuditoriaPort reintentos) {
        this.persistencia = Objects.requireNonNull(persistencia);
        this.trasCommit = Objects.requireNonNull(trasCommit);
        this.reintentos = Objects.requireNonNull(reintentos);
    }

    @Override
    public void registrarCritica(RegistroSeguro registro) {
        persistencia.guardar(aModelo(registro));
    }

    @Override
    public void registrarNoCriticaTrasCommit(RegistroSeguro registro) {
        RegistroAuditoria modelo = aModelo(registro);
        trasCommit.trasCommit(() -> {
            try {
                if (!persistencia.existeRegistro(modelo.registroId())) {
                    persistencia.guardar(modelo);
                }
            } catch (RuntimeException fallo) {
                try {
                    reintentos.diferir(registro);
                } catch (RuntimeException diferido) {
                    throw new RegistroDiferidoNoDisponibleException(diferido);
                }
            }
        });
    }

    @Override
    public PaginaActividad actividadPropia(UUID titular, int pagina, int tamano) {
        Objects.requireNonNull(titular, "titular");
        int p = Math.max(1, pagina);
        int t = Math.min(TAMANO_MAXIMO, Math.max(1, tamano));
        var resultado = persistencia.listarDeObjetivo(titular, p, t);
        return new PaginaActividad(resultado.registros().stream().map(AuditoriaService::aSeguro).toList(),
                resultado.total());
    }

    static RegistroAuditoria aModelo(RegistroSeguro r) {
        Objects.requireNonNull(r, "registro");
        return RegistroAuditoria.nuevo(r.registroId(), r.accion(), Resultado.valueOf(r.resultado()),
                ActorTipo.valueOf(r.actorTipo()), r.actorId(), r.objetivoUsuarioId(), r.fecha(), r.ip(),
                r.agente(), r.detalle());
    }

    static RegistroSeguro aSeguro(RegistroAuditoria m) {
        return new RegistroSeguro(m.registroId(), m.accion().name(), m.resultado().name(), m.actorTipo().name(),
                m.actorId(), m.objetivoUsuarioId(), m.fecha(), m.ip(), m.agente(), m.detalle());
    }
}
