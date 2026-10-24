package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.port.out.EfectosVerificacionPort;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Puente de la verificación hacia autorización (Sergio), mensajería y auditoría (Christian). */
public final class EfectosVerificacionAdapter implements EfectosVerificacionPort {

    private final AutorizacionPort autorizacion;
    private final MensajeriaPort mensajeria;
    private final AuditoriaPort auditoria;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public EfectosVerificacionAdapter(AutorizacionPort autorizacion, MensajeriaPort mensajeria, AuditoriaPort auditoria,
                                      PlatformTransactionManager transacciones, Clock reloj) {
        this.autorizacion = autorizacion;
        this.mensajeria = mensajeria;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public <T> T enTransaccion(Supplier<T> trabajo, Predicate<T> confirmar) {
        return transaccion.execute(estado -> {
            T resultado = trabajo.get();
            if (!confirmar.test(resultado)) estado.setRollbackOnly();
            return resultado;
        });
    }

    @Override
    public void publicarUsuarioCreado(UUID usuarioId, Instant ahora) {
        // El catálogo pide "roles" como lista, pero MensajeriaService.sobre() solo la arma para
        // usuario.roles_cambiados: por ahora viaja como texto separado por comas (pendiente con Christian).
        mensajeria.guardarEvento(new EventoUsuario(UUID.randomUUID(), "usuario.creado", Map.of(
                "usuarioId", usuarioId.toString(), "fecha", ahora.toString(),
                "roles", String.join(",", autorizacion.resolver(usuarioId).codigosRoles()))));
    }

    @Override
    public void auditarVerificacion(UUID usuarioId, String motivo, ContextoPeticion contexto) {
        boolean exito = motivo == null;
        var detalle = new LinkedHashMap<String, String>();
        detalle.put("via", "ENLACE");
        if (!exito) detalle.put("motivo", motivo);
        auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "USUARIO_VERIFICADO",
                exito ? "EXITO" : "FALLO", exito ? "USUARIO" : "SISTEMA", exito ? usuarioId.toString() : null,
                usuarioId, reloj.instant(), contexto.ip(), contexto.agente(), detalle));
    }
}
