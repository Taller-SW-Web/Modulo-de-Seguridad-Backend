package com.marketplace.auth_service.proteccion.application.service;
import com.marketplace.auth_service.proteccion.application.dto.*;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import com.marketplace.auth_service.proteccion.application.port.out.*;
import com.marketplace.auth_service.proteccion.domain.exception.ProteccionNoDisponibleException;
import com.marketplace.auth_service.proteccion.domain.model.Bloqueo;
import com.marketplace.auth_service.proteccion.domain.model.RachaFallo;
import com.marketplace.auth_service.proteccion.domain.model.TipoBloqueo;
import com.marketplace.auth_service.proteccion.domain.model.IntentoLogin;
import com.marketplace.auth_service.proteccion.domain.model.ResultadoLogin;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.*;
import java.util.*;
import static com.marketplace.auth_service.proteccion.application.dto.ResultadoFallo.Estado.*;

public final class ProteccionService implements ProteccionPort {
    private final PersistenciaProteccionPort persistencia;
    private final HuellaProteccionPort huellas;
    private final CuentasProteccionPort cuentas;
    private final TokensProteccionPort tokens;
    private final EfectosProteccionPort efectos;
    private final HashEnlaceProteccionPort hashes;
    private final Clock reloj;
    public ProteccionService(PersistenciaProteccionPort persistencia, HuellaProteccionPort huellas,
            CuentasProteccionPort cuentas, TokensProteccionPort tokens, EfectosProteccionPort efectos,
            HashEnlaceProteccionPort hashes, Clock reloj) {
        this.persistencia=persistencia; this.huellas=huellas; this.cuentas=cuentas;
        this.tokens=tokens; this.efectos=efectos; this.hashes=hashes; this.reloj=reloj;
    }
    @Override public EvaluacionProteccion evaluar(CuentaSnapshot cuenta, Instant ahora) {
        Objects.requireNonNull(cuenta); Objects.requireNonNull(ahora);
        if (!"ACTIVO".equals(cuenta.estadoBase()))
            return new EvaluacionProteccion(cuenta.estadoBase(),false,false,null,null,null,
                    cuenta.intentosFallidos(),cuenta.bloqueosSeguidos());
        var bloqueo=persistencia.buscarBloqueo(cuenta.usuarioId()).orElse(null);
        if (bloqueo != null && bloqueo.vigenteEn(ahora))
            return new EvaluacionProteccion("BLOQUEADO",false,bloqueo.tipo()==TipoBloqueo.AUTOMATICO,
                    bloqueo.tipo().name(),bloqueo.bloqueadoHasta(),bloqueo.revision(),
                    cuenta.intentosFallidos(),cuenta.bloqueosSeguidos());
        return new EvaluacionProteccion("ACTIVO",true,true,null,null,null,
                bloqueo == null ? cuenta.intentosFallidos() : 0,cuenta.bloqueosSeguidos());
    }
    @Override public ResultadoFallo registrarFallo(CuentaSnapshot recibida, HuellaFallo entrada, ContextoPeticion contexto) {
        Objects.requireNonNull(entrada); Objects.requireNonNull(contexto);
        Instant ahora=reloj.instant();
        CuentaSnapshot cuenta=recibida==null ? null : cuentas.leer(recibida.usuarioId()).orElse(null);
        persistencia.registrarIntento(new IntentoLogin(UUID.randomUUID(),
                cuenta==null ? null : cuenta.usuarioId(), cuenta==null ? entrada.correoIntentado() : cuenta.correo(),
                ResultadoLogin.FALLO,contexto.ip(),contexto.agente(),ahora));
        if (cuenta==null) return new ResultadoFallo(IGNORADO,0,0,null);
        if (!"ACTIVO".equals(cuenta.estadoBase()))
            return new ResultadoFallo(IGNORADO,cuenta.intentosFallidos(),cuenta.bloqueosSeguidos(),null);
        var bloqueo=persistencia.bloquearBloqueo(cuenta.usuarioId()).orElse(null);
        if (bloqueo!=null && bloqueo.vigenteEn(ahora))
            return new ResultadoFallo(IGNORADO,cuenta.intentosFallidos(),cuenta.bloqueosSeguidos(),bloqueo.revision());
        if (bloqueo!=null) {
            normalizarRacha(cuenta.usuarioId(),ahora);
            if (!persistencia.eliminarBloqueo(cuenta.usuarioId(),bloqueo.revision()))
                throw new IllegalStateException("Revisión del bloqueo cambió");
            cuentas.guardarContadores(cuenta.usuarioId(),cuenta.bloqueosSeguidos(),0);
            cuenta=new CuentaSnapshot(cuenta.usuarioId(),cuenta.correo(),cuenta.estadoBase(),cuenta.bloqueosSeguidos(),0);
        }
        RachaFallo racha=persistencia.bloquearRacha(cuenta.usuarioId()).orElse(null);
        if (racha==null) {
            if (cuenta.intentosFallidos()!=0) throw new ProteccionNoDisponibleException("racha histórica sin huellas verificables");
            racha=new RachaFallo(cuenta.usuarioId(),UUID.randomUUID(),huellas.claveActualId(),ahora);
            persistencia.crearRacha(racha);
        }
        byte[] secreto=entrada.copiarBytes();
        boolean nueva;
        try { nueva=persistencia.insertarHuellaSiNueva(huellas.generar(racha,secreto,ahora)); }
        finally { Arrays.fill(secreto,(byte)0); }
        if (!nueva) return new ResultadoFallo(REPETIDO,cuenta.intentosFallidos(),cuenta.bloqueosSeguidos(),null);
        int intentos=cuenta.intentosFallidos()+1;
        if (intentos>5) throw new IllegalStateException("Racha incoherente");
        cuentas.guardarContadores(cuenta.usuarioId(),cuenta.bloqueosSeguidos(),intentos);
        if (intentos<5) return new ResultadoFallo(CONTADO,intentos,cuenta.bloqueosSeguidos(),null);
        int seguidos=Math.addExact(cuenta.bloqueosSeguidos(),1);
        Instant hasta=seguidos>=4 ? null : ahora.plusSeconds(60L << (seguidos-1));
        UUID revision=UUID.randomUUID();
        persistencia.guardarBloqueo(new Bloqueo(cuenta.usuarioId(),TipoBloqueo.AUTOMATICO,hasta,null,ahora,revision));
        cuentas.guardarContadores(cuenta.usuarioId(),seguidos,5);
        tokens.invalidarPrevios(cuenta.usuarioId());
        EnlaceEmitido enlace=tokens.emitir(cuenta.usuarioId(),revision,Duration.ofMinutes(30));
        efectos.bloqueada(cuenta,revision,hasta,enlace,contexto,ahora);
        return new ResultadoFallo(BLOQUEADO,5,seguidos,revision);
    }
    @Override public void completarLogin(UUID usuarioId) {
        CuentaSnapshot cuenta=cuentas.leer(usuarioId).orElseThrow(() -> new IllegalStateException("Cuenta no disponible"));
        Instant ahora=reloj.instant();
        if (!evaluar(cuenta,ahora).permiteLogin()) throw new IllegalStateException("Autenticación no completada");
        var bloqueo=persistencia.bloquearBloqueo(usuarioId).orElse(null);
        normalizarRacha(usuarioId,ahora);
        if (bloqueo!=null && !persistencia.eliminarBloqueo(usuarioId,bloqueo.revision()))
            throw new IllegalStateException("Revisión del bloqueo cambió");
        cuentas.guardarContadores(usuarioId,0,0);
    }
    @Override public ResultadoDesbloqueo desbloquear(PruebaEnlace prueba, ContextoPeticion contexto) {
        String hash=hashes.calcular(prueba);
        TokenDesbloqueo orientativo=tokens.localizar(hash).orElse(null);
        if (!valido(orientativo)) return ResultadoDesbloqueo.INVALIDA;
        cuentas.bloquearCatalogoAdministrador();
        CuentaSnapshot cuenta=cuentas.bloquear(orientativo.usuarioId());
        if (!"ACTIVO".equals(cuenta.estadoBase())) return ResultadoDesbloqueo.INVALIDA;
        Bloqueo bloqueo=persistencia.bloquearBloqueo(cuenta.usuarioId()).orElse(null);
        if (bloqueo==null || bloqueo.tipo()!=TipoBloqueo.AUTOMATICO) return ResultadoDesbloqueo.INVALIDA;
        persistencia.bloquearRacha(cuenta.usuarioId()); // Cuenta → bloqueo/racha → token.
        TokenDesbloqueo actual=tokens.localizar(hash).orElse(null);
        if (!valido(actual) || !actual.id().equals(orientativo.id())
                || !actual.usuarioId().equals(cuenta.usuarioId())
                || !bloqueo.revision().equals(actual.contextoId())) return ResultadoDesbloqueo.INVALIDA;
        Instant ahora=reloj.instant();
        if (!ahora.isBefore(actual.expiraEn())) return ResultadoDesbloqueo.EXPIRADA;
        if (!tokens.consumir(actual.id(),cuenta.usuarioId(),bloqueo.revision(),ahora))
            return ResultadoDesbloqueo.INVALIDA;
        normalizarRacha(cuenta.usuarioId(),ahora);
        if (!persistencia.eliminarBloqueo(cuenta.usuarioId(),bloqueo.revision()))
            throw new IllegalStateException("Revisión del bloqueo cambió");
        cuentas.guardarContadores(cuenta.usuarioId(),cuenta.bloqueosSeguidos(),0);
        efectos.desbloqueada(cuenta,bloqueo.revision(),contexto,ahora);
        return ResultadoDesbloqueo.DESBLOQUEADA;
    }
    @Override public void prepararRestablecimiento(UUID usuarioId) {
        Objects.requireNonNull(usuarioId);
        persistencia.bloquearBloqueo(usuarioId);
        persistencia.bloquearRacha(usuarioId);
    }
    @Override public void restablecerPorRecuperacion(UUID usuarioId, ContextoPeticion contexto, Instant ahora) {
        Objects.requireNonNull(usuarioId); Objects.requireNonNull(contexto); Objects.requireNonNull(ahora);
        CuentaSnapshot cuenta=cuentas.leer(usuarioId).orElseThrow(() -> new IllegalStateException("Cuenta no disponible"));
        Bloqueo bloqueo=persistencia.bloquearBloqueo(usuarioId).orElse(null);
        RachaFallo racha=persistencia.bloquearRacha(usuarioId).orElse(null);
        if (bloqueo!=null && bloqueo.tipo()==TipoBloqueo.AUTOMATICO) {
            if (!persistencia.eliminarBloqueo(usuarioId,bloqueo.revision()))
                throw new IllegalStateException("Revisión del bloqueo cambió");
            efectos.desbloqueadaPorRestablecimiento(cuenta,bloqueo.revision(),contexto,ahora);
        }
        if (racha==null) {
            persistencia.crearRacha(new RachaFallo(usuarioId,UUID.randomUUID(),huellas.claveActualId(),ahora));
        } else {
            normalizarRacha(usuarioId,ahora);
        }
        // Preserva bloqueosSeguidos; el restablecimiento solo limpia intentosFallidos.
        cuentas.guardarContadores(usuarioId,cuenta.bloqueosSeguidos(),0);
    }
    private static boolean valido(TokenDesbloqueo t) {
        return t!=null && !t.usado() && t.contextoId()!=null
                && "DESBLOQUEO_AUTOMATICO".equals(t.proposito());
    }
    private void normalizarRacha(UUID usuarioId, Instant ahora) {
        RachaFallo nueva=new RachaFallo(usuarioId,UUID.randomUUID(),huellas.claveActualId(),ahora);
        if (persistencia.bloquearRacha(usuarioId).isPresent()) persistencia.reiniciarRacha(nueva);
        else persistencia.crearRacha(nueva);
    }
}
