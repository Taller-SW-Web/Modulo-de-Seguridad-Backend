package com.marketplace.auth_service.sesion.application.service;

import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.dto.CierreResultado;
import com.marketplace.auth_service.sesion.application.dto.ContextoSesion;
import com.marketplace.auth_service.sesion.application.dto.DatosAcceso;
import com.marketplace.auth_service.sesion.application.dto.DatosIdentidad;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.MotivoRechazo;
import com.marketplace.auth_service.sesion.application.dto.RefrescoGenerado;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.dto.TokenAccesoFirmado;
import com.marketplace.auth_service.sesion.application.dto.UsuarioAutenticado;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.CriptografiaSesionPort;
import com.marketplace.auth_service.sesion.application.port.out.FirmadorAccesoPort;
import com.marketplace.auth_service.sesion.application.port.out.PersistenciaSesionPort;
import com.marketplace.auth_service.sesion.application.port.out.ProveedorIdentidadPort;
import com.marketplace.auth_service.sesion.domain.exception.AccesoDesconocidoException;
import com.marketplace.auth_service.sesion.domain.exception.FamiliaRevocadaException;
import com.marketplace.auth_service.sesion.domain.model.AccesoSesion;
import com.marketplace.auth_service.sesion.domain.model.SesionFamilia;
import com.marketplace.auth_service.sesion.domain.model.TokenRefresco;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sesiones reales (EP-02-T003, SPEC-05 RF-05.6): abre familias con acceso
 * firmado y refresco opaco, resuelve accesos por jti verificado y revoca una,
 * todas u otras familias. Java puro: la transacción la abre el coordinador o
 * el decorador; un fallo técnico revierte familia, token y acceso juntos, sin
 * tokens huérfanos.
 */
public class SesionService implements SesionesPort {

    private final PersistenciaSesionPort persistencia;
    private final CriptografiaSesionPort criptografia;
    private final FirmadorAccesoPort firmador;
    private final ProveedorIdentidadPort identidad;
    private final Clock clock;

    public SesionService(PersistenciaSesionPort persistencia, CriptografiaSesionPort criptografia,
                         FirmadorAccesoPort firmador, ProveedorIdentidadPort identidad, Clock clock) {
        this.persistencia = Objects.requireNonNull(persistencia, "persistencia");
        this.criptografia = Objects.requireNonNull(criptografia, "criptografia");
        this.firmador = Objects.requireNonNull(firmador, "firmador");
        this.identidad = Objects.requireNonNull(identidad, "identidad");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SesionIniciada abrir(UUID usuarioId, IdentidadAutorizada identidad, ContextoPeticion contexto) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(identidad, "identidad");
        Objects.requireNonNull(contexto, "contexto");
        if (!identidad.usuarioId().equals(usuarioId)) {
            throw new IllegalArgumentException("la identidad resuelta no es del usuario");
        }
        Instant ahora = clock.instant();
        SesionFamilia familia = SesionFamilia.nueva(UUID.randomUUID(), usuarioId, ahora);
        persistencia.guardarFamilia(familia);
        RefrescoGenerado refresco = criptografia.generarRefresco();
        TokenRefresco token = TokenRefresco.nuevo(UUID.randomUUID(), usuarioId, familia.id(),
                refresco.hash(), ahora);
        persistencia.guardarToken(token);
        TokenAccesoFirmado firmado = firmador.firmar(
                new DatosAcceso(usuarioId, identidad.correo(), identidad.roles(), identidad.permisos()));
        persistencia.guardarAcceso(new AccesoSesion(firmado.jti(), usuarioId, familia.id(),
                firmado.expiraEn(), ahora));
        return new SesionIniciada(firmado.token(), refresco.secreto(), firmado.expiresIn(),
                firmado.expiraEn(), token.expiracion(),
                new UsuarioAutenticado(usuarioId, identidad.nombreCompleto(), identidad.correo(),
                        identidad.roles()));
    }

    @Override
    public ContextoSesion resolverAcceso(AccesoVerificado acceso) {
        Objects.requireNonNull(acceso, "acceso");
        Instant ahora = clock.instant();
        AccesoSesion asociacion = persistencia.buscarAccesoPorJti(acceso.jti())
                .filter(fila -> fila.usuarioId().equals(acceso.sub()) && ahora.isBefore(fila.expiraEn()))
                .orElseThrow(() -> new AccesoDesconocidoException(acceso.jti()));
        SesionFamilia familia = persistencia.bloquearFamilia(asociacion.familiaId())
                .filter(f -> f.usuarioId().equals(asociacion.usuarioId()))
                .orElseThrow(() -> new AccesoDesconocidoException(acceso.jti()));
        if (familia.revocada()) {
            throw new FamiliaRevocadaException(familia.id());
        }
        return new ContextoSesion(asociacion.usuarioId(), asociacion.familiaId(), asociacion.jti(),
                asociacion.expiraEn());
    }

    @Override
    public RenovacionResultado renovar(SecretoRefresco secreto) {
        Objects.requireNonNull(secreto, "secreto");
        TokenRefresco presentado = persistencia.buscarTokenPorHash(criptografia.hash(secreto.valor()))
                .orElse(null);
        if (presentado == null) {
            return new RenovacionResultado.Rechazada(MotivoRechazo.REFRESCO_DESCONOCIDO, null, null);
        }
        SesionFamilia familia = persistencia.bloquearFamilia(presentado.familiaId()).orElse(null);
        if (familia == null) {
            return new RenovacionResultado.Rechazada(MotivoRechazo.REFRESCO_DESCONOCIDO,
                    presentado.usuarioId(), presentado.familiaId());
        }
        Instant ahora = clock.instant(); // Tras esperar el lock, no antes.
        // La lectura previa al lock es orientativa: se relee bajo el lock de la
        // raíz, así dos renovaciones simultáneas no emiten dos sucesores.
        TokenRefresco actual = persistencia.buscarTokenPorHash(criptografia.hash(secreto.valor()))
                .filter(fila -> fila.familiaId().equals(familia.id())).orElse(null);
        if (actual == null) {
            return new RenovacionResultado.Rechazada(MotivoRechazo.REFRESCO_DESCONOCIDO,
                    presentado.usuarioId(), familia.id());
        }
        if (familia.revocada() || actual.revocado()) {
            return new RenovacionResultado.Rechazada(MotivoRechazo.REFRESCO_REVOCADO,
                    actual.usuarioId(), familia.id());
        }
        if (actual.usado()) {
            revocarFamilia(familia.id(), ahora);
            return new RenovacionResultado.Rechazada(MotivoRechazo.REUSO_DETECTADO,
                    actual.usuarioId(), familia.id());
        }
        if (!ahora.isBefore(actual.expiracion())) {
            return new RenovacionResultado.Rechazada(MotivoRechazo.REFRESCO_VENCIDO,
                    actual.usuarioId(), familia.id());
        }
        DatosIdentidad datos = identidad.identidadParaRenovar(actual.usuarioId());
        if (!datos.usuarioId().equals(actual.usuarioId()) || !datos.renovacionPermitida()) {
            return new RenovacionResultado.Rechazada(MotivoRechazo.CUENTA_NO_DISPONIBLE,
                    actual.usuarioId(), familia.id());
        }
        persistencia.guardarToken(actual.marcarUsado());
        RefrescoGenerado siguiente = criptografia.generarRefresco();
        TokenRefresco nuevo = TokenRefresco.nuevo(UUID.randomUUID(), actual.usuarioId(), familia.id(),
                siguiente.hash(), ahora);
        persistencia.guardarToken(nuevo);
        TokenAccesoFirmado firmado = firmador.firmar(
                new DatosAcceso(actual.usuarioId(), datos.email(), datos.roles(), datos.permisos()));
        persistencia.guardarAcceso(new AccesoSesion(firmado.jti(), actual.usuarioId(), familia.id(),
                firmado.expiraEn(), ahora));
        return new RenovacionResultado.Renovada(new SesionIniciada(firmado.token(), siguiente.secreto(),
                firmado.expiresIn(), firmado.expiraEn(), nuevo.expiracion(),
                new UsuarioAutenticado(actual.usuarioId(), datos.nombreCompleto(), datos.email(),
                        datos.roles())));
    }

    @Override
    public CierreResultado cerrar(SecretoRefresco secreto) {
        Objects.requireNonNull(secreto, "secreto");
        Instant ahora = clock.instant();
        TokenRefresco presentado = persistencia.buscarTokenPorHash(criptografia.hash(secreto.valor()))
                .orElse(null);
        if (presentado == null) {
            return new CierreResultado.YaCerrada();
        }
        SesionFamilia familia = persistencia.bloquearFamilia(presentado.familiaId()).orElse(null);
        if (familia == null || familia.revocada()) {
            return new CierreResultado.YaCerrada();
        }
        revocarFamilia(familia.id(), ahora);
        return new CierreResultado.Cerrada(presentado.usuarioId(), familia.id());
    }

    @Override
    public void revocarTodas(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        for (SesionFamilia familia : persistencia.listarFamiliasDeUsuario(usuarioId)) {
            revocarFamilia(familia.id(), clock.instant());
        }
    }

    @Override
    public void revocarOtras(UUID usuarioId, UUID familiaActual) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(familiaActual, "familiaActual");
        for (SesionFamilia familia : persistencia.listarFamiliasDeUsuario(usuarioId)) {
            if (!familia.id().equals(familiaActual)) {
                revocarFamilia(familia.id(), clock.instant());
            }
        }
    }

    private void revocarFamilia(UUID familiaId, Instant ahora) {
        persistencia.bloquearFamilia(familiaId).ifPresent(familia -> {
            if (!familia.revocada()) {
                persistencia.guardarFamilia(familia.revocar(ahora));
            }
            for (TokenRefresco token : persistencia.listarTokensDeFamilia(familiaId)) {
                if (!token.revocado()) {
                    persistencia.guardarToken(token.revocar());
                }
            }
        });
    }
}
