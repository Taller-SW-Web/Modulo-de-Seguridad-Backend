package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.in.RolInicialPersonalInterna;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.port.out.EfectosAltaPersonalPort;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Puente del alta administrativa hacia autorización (Sergio) y auditoría (Christian). */
public final class EfectosAltaPersonalAdapter implements EfectosAltaPersonalPort {

    private final AutorizacionPort autorizacion;
    private final RolInicialPersonalInterna roles;
    private final AuditoriaPort auditoria;
    private final TransactionTemplate transaccion;

    public EfectosAltaPersonalAdapter(AutorizacionPort autorizacion, RolInicialPersonalInterna roles,
                                      AuditoriaPort auditoria, PlatformTransactionManager transacciones) {
        this.autorizacion = autorizacion;
        this.roles = roles;
        this.auditoria = auditoria;
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
    public boolean puedeCrear(UUID actor) {
        // Igual que la baja: el permiso y el rol se leen de la base, no del token (pudo retirarse).
        var actual = autorizacion.resolver(actor);
        return actual.roles().contains(Rol.ADMIN_SISTEMA) && actual.permisos().contains(Permiso.USUARIO_CREAR);
    }

    @Override
    public void asignarRolInicial(UUID usuarioId, Rol rol) {
        roles.asignar(usuarioId, rol);
    }

    @Override
    public void auditarCreacion(UUID usuarioId, Rol rol, ActorUsuario actor, ContextoPeticion contexto, Instant ahora) {
        auditoria.registrarCritica(new RegistroSeguro(UUID.randomUUID(), "USUARIO_CREADO", "EXITO", "USUARIO",
                actor.sub().toString(), usuarioId, ahora, contexto.ip(), contexto.agente(),
                Map.of("via", "ALTA_ADMINISTRATIVA", "rol", rol == null ? "SIN_ROL" : rol.name())));
    }

    @Override
    public void auditarRechazo(String motivo, ActorUsuario actor, ContextoPeticion contexto, Instant ahora) {
        // Sin objetivo: el registro no identifica la cuenta que ya tenía ese correo.
        auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "USUARIO_CREADO", "FALLO",
                "USUARIO", actor.sub().toString(), null, ahora, contexto.ip(), contexto.agente(),
                Map.of("via", "ALTA_ADMINISTRATIVA", "motivo", motivo)));
    }

    @Override
    public void denegado(ActorUsuario actor, ContextoPeticion contexto, Instant ahora) {
        try {
            auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "ACCESO_DENEGADO", "FALLO",
                    "USUARIO", actor.sub().toString(), null, ahora, contexto.ip(), contexto.agente(),
                    Map.of("via", "ALTA_ADMINISTRATIVA")));
        } catch (RuntimeException noCritica) {
            // La indisponibilidad de la auditoría no concede acceso ni cambia el 403.
        }
    }
}
