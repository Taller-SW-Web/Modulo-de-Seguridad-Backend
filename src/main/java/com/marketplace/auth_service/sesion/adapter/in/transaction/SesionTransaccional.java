package com.marketplace.auth_service.sesion.adapter.in.transaction;

import com.marketplace.auth_service.sesion.application.dto.*;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.*;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.springframework.transaction.annotation.*;
import java.util.UUID;

/** Bean único. Otros coordinadores ya bloquean cuenta; refresh/logout toman
 * cuenta antes de raíz. El HTTP se decide después del commit. */
public class SesionTransaccional implements SesionesPort {
    private final SesionesPort casoDeUso;
    private final PersistenciaSesionPort persistencia;
    private final CriptografiaSesionPort criptografia;
    private final BloqueoCuentaSesionPort cuentas;
    public SesionTransaccional(SesionesPort casoDeUso, PersistenciaSesionPort persistencia,
            CriptografiaSesionPort criptografia, BloqueoCuentaSesionPort cuentas) {
        this.casoDeUso = casoDeUso;
        this.persistencia = persistencia;
        this.criptografia = criptografia;
        this.cuentas = cuentas;
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public SesionIniciada abrir(UUID usuarioId, IdentidadAutorizada identidad, ContextoPeticion contexto) {
        return casoDeUso.abrir(usuarioId, identidad, contexto);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public ContextoSesion resolverAcceso(AccesoVerificado acceso) { return casoDeUso.resolverAcceso(acceso); }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public RenovacionResultado renovar(SecretoRefresco secreto) {
        bloquearCuentaDelRefresco(secreto);
        return casoDeUso.renovar(secreto);
    }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public CierreResultado cerrar(SecretoRefresco secreto, ContextoPeticion contexto) {
        bloquearCuentaDelRefresco(secreto);
        return casoDeUso.cerrar(secreto, contexto);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void revocarTodas(UUID usuarioId) { casoDeUso.revocarTodas(usuarioId); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void revocarOtras(UUID usuarioId, UUID familiaActual) { casoDeUso.revocarOtras(usuarioId, familiaActual); }
    private void bloquearCuentaDelRefresco(SecretoRefresco secreto) {
        persistencia.buscarTokenPorHash(criptografia.hash(secreto.valor()))
                .ifPresent(token -> cuentas.bloquear(token.usuarioId()));
    }
}
