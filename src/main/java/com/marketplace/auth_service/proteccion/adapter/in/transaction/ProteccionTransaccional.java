package com.marketplace.auth_service.proteccion.adapter.in.transaction;
import com.marketplace.auth_service.proteccion.application.dto.*;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.UUID;
public class ProteccionTransaccional implements ProteccionPort {
    private final ProteccionPort delegado;
    public ProteccionTransaccional(ProteccionPort delegado) { this.delegado=delegado; }
    @Override @Transactional(readOnly=true) public EvaluacionProteccion evaluar(CuentaSnapshot cuenta, Instant ahora) {
        return delegado.evaluar(cuenta,ahora);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ResultadoFallo registrarFallo(CuentaSnapshot cuenta, HuellaFallo huella, ContextoPeticion contexto) {
        return delegado.registrarFallo(cuenta,huella,contexto);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void completarLogin(UUID usuarioId) { delegado.completarLogin(usuarioId); }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public ResultadoDesbloqueo desbloquear(PruebaEnlace prueba, ContextoPeticion contexto) {
        return delegado.desbloquear(prueba,contexto);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void prepararRestablecimiento(UUID usuarioId) { delegado.prepararRestablecimiento(usuarioId); }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void restablecerPorRecuperacion(UUID usuarioId, ContextoPeticion contexto, Instant ahora) {
        delegado.restablecerPorRecuperacion(usuarioId,contexto,ahora);
    }
}
