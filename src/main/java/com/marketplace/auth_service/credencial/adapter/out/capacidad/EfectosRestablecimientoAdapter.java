package com.marketplace.auth_service.credencial.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.application.port.out.EfectosRestablecimientoPort;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Puente local: cada capacidad se consume por su puerto exportado. */
public final class EfectosRestablecimientoAdapter implements EfectosRestablecimientoPort {
    private final CuentasPort cuentas;
    private final ProteccionPort proteccion;
    private final SesionesPort sesiones;
    private final AuditoriaPort auditoria;
    private final MensajeriaPort mensajeria;
    private final TransactionTemplate transaccion;

    public EfectosRestablecimientoAdapter(CuentasPort cuentas, ProteccionPort proteccion,
            SesionesPort sesiones, AuditoriaPort auditoria, MensajeriaPort mensajeria,
            PlatformTransactionManager transacciones) {
        this.cuentas=Objects.requireNonNull(cuentas); this.proteccion=Objects.requireNonNull(proteccion);
        this.sesiones=Objects.requireNonNull(sesiones); this.auditoria=Objects.requireNonNull(auditoria);
        this.mensajeria=Objects.requireNonNull(mensajeria);
        this.transaccion=new TransactionTemplate(Objects.requireNonNull(transacciones));
        this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @Override public <T> T enTransaccion(Supplier<T> trabajo) { return transaccion.execute(estado -> trabajo.get()); }
    @Override public void bloquearCuenta(UUID usuarioId) { cuentas.bloquear(usuarioId); }
    @Override public void prepararProteccion(UUID usuarioId) { proteccion.prepararRestablecimiento(usuarioId); }
    @Override public void restablecerProteccion(UUID usuarioId, ContextoPeticion contexto, Instant ahora) {
        proteccion.restablecerPorRecuperacion(usuarioId,contexto,ahora);
    }
    @Override public void revocarSesiones(UUID usuarioId) { sesiones.revocarTodas(usuarioId); }
    @Override public void auditar(UUID usuarioId, ContextoPeticion contexto, Instant ahora) {
        auditoria.registrarCritica(new RegistroSeguro(UUID.randomUUID(),"CONTRASENA_RESTABLECIDA","EXITO",
                "USUARIO",usuarioId.toString(),usuarioId,ahora,contexto.ip(),contexto.agente(),Map.of()));
    }
    @Override public void avisar(UUID usuarioId) {
        var cuenta=cuentas.leer(usuarioId).orElseThrow(() -> new IllegalStateException("Cuenta no disponible para el aviso"));
        mensajeria.guardarCorreo(new EntregaCorreo(UUID.randomUUID(),"contrasena_cambiada",cuenta.correo(),
                "La contraseña de tu cuenta fue restablecida. Si no realizaste este cambio, contacta con soporte."));
    }
}
