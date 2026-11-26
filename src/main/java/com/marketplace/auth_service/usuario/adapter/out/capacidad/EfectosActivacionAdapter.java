package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CredencialInicialInterna;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.port.out.EfectosActivacionPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Puente de la activación hacia credenciales (Juan José), autorización (Sergio), mensajería y auditoría. */
public final class EfectosActivacionAdapter implements EfectosActivacionPort {

    private final CredencialesPort politica;
    private final CredencialInicialInterna credenciales;
    private final AutorizacionPort autorizacion;
    private final MensajeriaPort mensajeria;
    private final AuditoriaPort auditoria;
    private final TransactionTemplate transaccion;

    public EfectosActivacionAdapter(CredencialesPort politica, CredencialInicialInterna credenciales,
                                    AutorizacionPort autorizacion, MensajeriaPort mensajeria, AuditoriaPort auditoria,
                                    PlatformTransactionManager transacciones) {
        this.politica = politica;
        this.credenciales = credenciales;
        this.autorizacion = autorizacion;
        this.mensajeria = mensajeria;
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
    public List<ResultadoRegistro.ReglaIncumplida> validarContrasena(UUID usuarioId, String contrasena) {
        return politica.validarNueva(usuarioId, new SecretoEfimero(contrasena)).errores().stream()
                .map(e -> new ResultadoRegistro.ReglaIncumplida(e.regla().name(), e.esperado()))
                .toList();
    }

    @Override
    public void crearCredencial(UUID usuarioId, String contrasena) {
        credenciales.crear(usuarioId, new SecretoEfimero(contrasena));
    }

    @Override
    public void registrarActivacion(UUID usuarioId, ContextoPeticion contexto, Instant ahora) {
        // Crítica: si el registro falla, la cuenta no se activa (RF-12.6).
        auditoria.registrarCritica(new RegistroSeguro(UUID.randomUUID(), "CUENTA_ACTIVADA", "EXITO", "USUARIO",
                usuarioId.toString(), usuarioId, ahora, contexto.ip(), contexto.agente(), Map.of("via", "ENLACE")));
        // Los roles van separados por comas en la carga; MensajeriaService.sobre() los publica como lista
        // (vacía para una cuenta sin rol, ESC-03.6).
        mensajeria.guardarEvento(new EventoUsuario(UUID.randomUUID(), "usuario.creado", Map.of(
                "usuarioId", usuarioId.toString(), "fecha", ahora.toString(),
                "roles", String.join(",", autorizacion.resolver(usuarioId).codigosRoles()))));
    }

    @Override
    public void auditarFallo(UUID usuarioId, String motivo, ContextoPeticion contexto, Instant ahora) {
        auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "CUENTA_ACTIVADA", "FALLO",
                "SISTEMA", null, usuarioId, ahora, contexto.ip(), contexto.agente(),
                Map.of("via", "ENLACE", "motivo", motivo)));
    }
}
