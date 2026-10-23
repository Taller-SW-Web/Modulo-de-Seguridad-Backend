package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.dto.InspeccionAcceso;
import com.marketplace.auth_service.identidad.application.dto.ResultadoIntrospeccion;
import com.marketplace.auth_service.identidad.application.dto.ResultadoIntrospeccion.Motivo;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.port.in.IntrospeccionPort;
import com.marketplace.auth_service.identidad.application.port.out.EstadoIdentidadPort;
import com.marketplace.auth_service.shared.domain.ActorServicio;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

/**
 * Introspección (SPEC-17 RF-17.6/17.7). Responde el estado <b>de ahora</b>, en
 * este orden, y el primer problema decide el motivo:
 *
 * <ol>
 *   <li>Firma, emisor y perfil de persona: si no, {@code FIRMA_INVALIDA}; si solo
 *       venció, {@code TOKEN_VENCIDO}.</li>
 *   <li>Sesión: el {@code jti} debe seguir en una familia no revocada (logout,
 *       reúso, cambio de contraseña o de roles la revocan) → {@code TOKEN_REVOCADO}.</li>
 *   <li>Cuenta: inexistente, inactiva o pendiente → {@code USUARIO_INACTIVO};
 *       bloqueada → {@code USUARIO_BLOQUEADO}.</li>
 *   <li>Roles: si ya no son los del token, el acceso no representa a la persona
 *       de hoy → {@code TOKEN_REVOCADO} (RF-17.7).</li>
 * </ol>
 *
 * <p>Firma válida no equivale a sesión activa: por eso se consulta la sesión y no
 * solo la cuenta por {@code sub}.
 */
public final class IntrospeccionService implements IntrospeccionPort {

    private final FirmaPort firma;
    private final EstadoIdentidadPort estado;

    public IntrospeccionService(FirmaPort firma, EstadoIdentidadPort estado) {
        this.firma = Objects.requireNonNull(firma, "firma");
        this.estado = Objects.requireNonNull(estado, "estado");
    }

    @Override
    public ResultadoIntrospeccion introspeccionar(ActorServicio solicitante, String token) {
        Objects.requireNonNull(solicitante, "solicitante");
        InspeccionAcceso inspeccion = firma.inspeccionar(token);
        if (inspeccion instanceof InspeccionAcceso.Vencido) {
            return new ResultadoIntrospeccion.Inactiva(Motivo.TOKEN_VENCIDO);
        }
        if (!(inspeccion instanceof InspeccionAcceso.Valido valido)) {
            return new ResultadoIntrospeccion.Inactiva(Motivo.FIRMA_INVALIDA);
        }
        AccesoValidado acceso = valido.acceso();
        if (!estado.accesoVigente(acceso.sub(), acceso.jti())) {
            return new ResultadoIntrospeccion.Inactiva(Motivo.TOKEN_REVOCADO);
        }
        Optional<EstadoIdentidadPort.Cuenta> cuenta = estado.cuenta(acceso.sub());
        if (cuenta.isEmpty()) {
            return new ResultadoIntrospeccion.Inactiva(Motivo.USUARIO_INACTIVO);
        }
        switch (cuenta.get().estadoEfectivo()) {
            case "ACTIVO" -> { }
            case "BLOQUEADO" -> {
                return new ResultadoIntrospeccion.Inactiva(Motivo.USUARIO_BLOQUEADO);
            }
            default -> {
                return new ResultadoIntrospeccion.Inactiva(Motivo.USUARIO_INACTIVO);
            }
        }
        EstadoIdentidadPort.Permisos actuales = estado.permisos(acceso.sub());
        if (!new HashSet<>(actuales.roles()).equals(new HashSet<>(acceso.roles()))) {
            return new ResultadoIntrospeccion.Inactiva(Motivo.TOKEN_REVOCADO);
        }
        return new ResultadoIntrospeccion.Activa(acceso.sub(), cuenta.get().correo(), actuales.roles(),
                actuales.permisos(), acceso.expiraEn());
    }
}
