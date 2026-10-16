package com.marketplace.auth_service.credencial.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.application.port.out.EfectosCambioContrasenaPort;
import com.marketplace.auth_service.credencial.application.dto.ResultadoCambioContrasena;
import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

/** Puente del cambio de contraseña hacia cuentas (Eva), sesiones (EP-02) y auditoría (Christian). */
public final class EfectosCambioContrasenaAdapter implements EfectosCambioContrasenaPort {

    private final CuentasPort cuentas;
    private final SesionesPort sesiones;
    private final AuditoriaPort auditoria;
    private final MensajeriaPort mensajeria;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public EfectosCambioContrasenaAdapter(CuentasPort cuentas, SesionesPort sesiones, AuditoriaPort auditoria, MensajeriaPort mensajeria,
                                          PlatformTransactionManager transacciones, Clock reloj) {
        this.cuentas = cuentas;
        this.sesiones = sesiones;
        this.auditoria = auditoria;
        this.mensajeria = mensajeria;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public <T> T enTransaccion(Supplier<T> trabajo) {
        return transaccion.execute(estado -> {
            T resultado = trabajo.get();
            // El proveedor de sesiones puede marcar rollback al rechazar la familia.
            // Hacerlo explícito localmente permite devolver el rechazo sin intentar commit.
            if (resultado == ResultadoCambioContrasena.Simple.SESION_INVALIDA) estado.setRollbackOnly();
            return resultado;
        });
    }

    @Override
    public void bloquearCuenta(UUID usuarioId) {
        cuentas.bloquear(usuarioId);
    }

    @Override
    public Optional<UUID> familiaDe(UUID usuarioId, UUID jti) {
        try {
            return Optional.of(sesiones.resolverAcceso(new AccesoVerificado(usuarioId, jti)).familiaId());
        } catch (DataAccessException | TransactionException falloTecnico) {
            throw falloTecnico;
        } catch (RuntimeException accesoDesconocidoORevocado) {
            return Optional.empty();
        }
    }

    @Override
    public void revocarOtras(UUID usuarioId, UUID familiaActual) {
        sesiones.revocarOtras(usuarioId, familiaActual);
    }

    @Override
    public void revocarTodas(UUID usuarioId) {
        sesiones.revocarTodas(usuarioId);
    }

    @Override
    public void auditarCambio(UUID usuarioId, ContextoPeticion contexto) {
        auditoria.registrarCritica(new RegistroSeguro(UUID.randomUUID(), "CONTRASENA_CAMBIADA", "EXITO", "USUARIO",
                usuarioId.toString(), usuarioId, reloj.instant(), contexto.ip(), contexto.agente(),
                Map.of("via", "CAMBIO_PROPIO")));
    }

    @Override
    public void avisarCambio(UUID usuarioId) {
        var cuenta = cuentas.leer(usuarioId)
                .orElseThrow(() -> new IllegalStateException("Cuenta no disponible para el aviso"));
        mensajeria.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "contrasena_cambiada", cuenta.correo(),
                "La contraseña de tu cuenta fue cambiada. Si no realizaste este cambio, contacta con soporte."));
    }
}
