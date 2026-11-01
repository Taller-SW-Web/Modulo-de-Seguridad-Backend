package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.RolClienteInicialInterna;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CredencialInicialInterna;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.port.out.EfectosRegistroPort;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Puente del registro hacia credenciales (Juan José), autorización (Sergio) y auditoría (Christian). */
public final class EfectosRegistroAdapter implements EfectosRegistroPort {

    private final CredencialesPort politica;
    private final CredencialInicialInterna credenciales;
    private final RolClienteInicialInterna roles;
    private final AuditoriaPort auditoria;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public EfectosRegistroAdapter(CredencialesPort politica, CredencialInicialInterna credenciales,
                                  RolClienteInicialInterna roles, AuditoriaPort auditoria,
                                  PlatformTransactionManager transacciones, Clock reloj) {
        this.politica = politica;
        this.credenciales = credenciales;
        this.roles = roles;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public ResultadoRegistro enTransaccion(Supplier<ResultadoRegistro> trabajo) {
        return transaccion.execute(estado -> {
            ResultadoRegistro resultado = trabajo.get();
            if (!(resultado instanceof ResultadoRegistro.Creada)) estado.setRollbackOnly();
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
    public void asignarRolCliente(UUID usuarioId) {
        roles.asignar(usuarioId);
    }

    @Override
    public void auditarCreacion(UUID usuarioId, String canalOrigen, ContextoPeticion contexto) {
        auditoria.registrarCritica(new RegistroSeguro(UUID.randomUUID(), "USUARIO_CREADO", "EXITO", "USUARIO",
                usuarioId.toString(), usuarioId, reloj.instant(), contexto.ip(), contexto.agente(),
                Map.of("via", "REGISTRO_PUBLICO", "canalOrigen", canalOrigen)));
    }

    @Override
    public void auditarRechazo(String motivo, String canalOrigen, List<String> campos, ContextoPeticion contexto) {
        // Sin actor ni objetivo: identificar la cuenta existente revelaría que el correo está registrado.
        // Solo nombres de campos, nunca valores: el detalle no admite contraseñas (RF-12.5).
        var detalle = new LinkedHashMap<String, String>();
        detalle.put("via", "REGISTRO_PUBLICO");
        detalle.put("motivo", motivo);
        if (canalOrigen != null) detalle.put("canalOrigen", canalOrigen);
        if (!campos.isEmpty()) detalle.put("campos", String.join(",", campos));
        auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "USUARIO_CREADO", "FALLO",
                "SISTEMA", null, null, reloj.instant(), contexto.ip(), contexto.agente(), detalle));
    }
}
