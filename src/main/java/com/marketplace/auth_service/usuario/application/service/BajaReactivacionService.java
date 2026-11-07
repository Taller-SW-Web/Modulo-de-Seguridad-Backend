package com.marketplace.auth_service.usuario.application.service;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAdministracionCuenta;
import com.marketplace.auth_service.usuario.application.port.in.BajaReactivacionPort;
import com.marketplace.auth_service.usuario.application.port.out.EfectosBajaReactivacionPort;
import com.marketplace.auth_service.usuario.application.port.out.EstadoCuentaPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Baja lógica y reactivación (SPEC-04, FEAT-02), con el mismo orden que el bloqueo manual
 * (SPEC-15): permiso → catálogo de administradores → cuentas bloqueadas en orden → nueva
 * comprobación → protección → efectos. Estado, sesiones, auditoría crítica y evento comparten la
 * transacción: si la auditoría falla, la baja se revierte (RF-04.4).
 */
public final class BajaReactivacionService implements BajaReactivacionPort {

    private final UsuarioRepositoryPort cuentas;
    private final EstadoCuentaPort estados;
    private final EfectosBajaReactivacionPort efectos;
    private final Clock reloj;

    public BajaReactivacionService(UsuarioRepositoryPort cuentas, EstadoCuentaPort estados,
                                   EfectosBajaReactivacionPort efectos, Clock reloj) {
        this.cuentas = Objects.requireNonNull(cuentas);
        this.estados = Objects.requireNonNull(estados);
        this.efectos = Objects.requireNonNull(efectos);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public ResultadoAdministracionCuenta darDeBaja(ActorUsuario actor, String usuarioId, ContextoPeticion contexto) {
        if (!efectos.autorizado(actor.sub(), Permiso.USUARIO_DESACTIVAR)) return denegar(actor, contexto);
        UUID id = identificador(usuarioId);
        if (id == null) return ResultadoAdministracionCuenta.VALIDACION;
        efectos.bloquearCatalogoAdministrador();
        if (cuentas.findById(id).isEmpty()) return ResultadoAdministracionCuenta.NO_ENCONTRADO;
        // Mismo orden de locks que el bloqueo manual: dos administradores cruzados no se interbloquean.
        Stream.of(actor.sub(), id).distinct().sorted(Comparator.comparing(UUID::toString)).forEach(cuentas::lockById);
        if (!efectos.autorizado(actor.sub(), Permiso.USUARIO_DESACTIVAR) || !activa(cuentas.findById(actor.sub()).orElseThrow()))
            return denegar(actor, contexto);
        if (id.equals(actor.sub())) return ResultadoAdministracionCuenta.ADMINISTRADOR_PROTEGIDO;
        var cuenta = cuentas.findById(id).orElseThrow();
        if (EstadoCuenta.INACTIVO.name().equals(cuenta.estadoBase())) return ResultadoAdministracionCuenta.HECHO;
        if (efectos.esAdministrador(id) && otrosAdministradoresActivos(id) == 0)
            return ResultadoAdministracionCuenta.ADMINISTRADOR_PROTEGIDO;
        var ahora = reloj.instant();
        estados.cambiar(id, EstadoCuenta.INACTIVO, ahora);
        efectos.revocarSesiones(id);
        efectos.registrarBaja(id, actor, contexto, ahora);
        return ResultadoAdministracionCuenta.HECHO;
    }

    @Override
    public ResultadoAdministracionCuenta reactivar(ActorUsuario actor, String usuarioId, ContextoPeticion contexto) {
        if (!efectos.autorizado(actor.sub(), Permiso.USUARIO_REACTIVAR)) return denegar(actor, contexto);
        UUID id = identificador(usuarioId);
        if (id == null) return ResultadoAdministracionCuenta.VALIDACION;
        if (cuentas.findById(id).isEmpty()) return ResultadoAdministracionCuenta.NO_ENCONTRADO;
        var cuenta = cuentas.lockById(id);
        // RF-04.5: sobre una cuenta que no está dada de baja no cambia nada y responde igual.
        if (!EstadoCuenta.INACTIVO.name().equals(cuenta.estadoBase())) return ResultadoAdministracionCuenta.HECHO;
        // Una cuenta que se dio de baja sin verificar su correo vuelve pendiente: reactivar no salta SPEC-02.
        var destino = cuenta.correoVerificado() ? EstadoCuenta.ACTIVO : EstadoCuenta.PENDIENTE_VERIFICACION;
        var ahora = reloj.instant();
        estados.cambiar(id, destino, ahora);
        efectos.registrarReactivacion(id, actor, contexto, ahora);
        return ResultadoAdministracionCuenta.HECHO;
    }

    private long otrosAdministradoresActivos(UUID id) {
        var otros = new HashSet<>(efectos.administradores());
        otros.remove(id);
        return otros.isEmpty() ? 0 : cuentas.countActiveByIds(otros);
    }

    private static boolean activa(CuentaSnapshot cuenta) {
        return EstadoCuenta.ACTIVO.name().equals(cuenta.estadoBase());
    }

    private static UUID identificador(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException | NullPointerException invalido) {
            return null;
        }
    }

    private ResultadoAdministracionCuenta denegar(ActorUsuario actor, ContextoPeticion contexto) {
        efectos.denegado(actor, contexto, reloj.instant());
        return ResultadoAdministracionCuenta.SIN_PERMISO;
    }
}
