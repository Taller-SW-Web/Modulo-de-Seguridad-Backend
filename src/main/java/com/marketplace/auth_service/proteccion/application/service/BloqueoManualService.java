package com.marketplace.auth_service.proteccion.application.service;

import com.marketplace.auth_service.proteccion.application.port.in.BloqueoManualPort;
import com.marketplace.auth_service.proteccion.application.port.out.*;
import com.marketplace.auth_service.proteccion.domain.model.*;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/** SPEC-15: guard de administradores → cuentas ordenadas → familias → protección → efectos. */
public final class BloqueoManualService implements BloqueoManualPort {
    private final CuentasProteccionPort cuentas;
    private final PersistenciaProteccionPort persistencia;
    private final TokensProteccionPort tokens;
    private final HuellaProteccionPort huellas;
    private final OperacionManualPort operaciones;
    private final Clock reloj;

    public BloqueoManualService(CuentasProteccionPort cuentas, PersistenciaProteccionPort persistencia,
            TokensProteccionPort tokens, HuellaProteccionPort huellas, OperacionManualPort operaciones, Clock reloj) {
        this.cuentas=cuentas; this.persistencia=persistencia; this.tokens=tokens;
        this.huellas=huellas; this.operaciones=operaciones; this.reloj=reloj;
    }

    @Override public String ejecutar(ActorUsuario actor, String objetivo, boolean bloquear, String motivo, ContextoPeticion contexto) {
        if (!operaciones.autorizado(actor)) return denegar(actor, contexto);
        UUID id;
        try { id=UUID.fromString(objetivo); } catch (IllegalArgumentException | NullPointerException e) { return "VALIDACION"; }
        if (bloquear && (motivo==null || motivo.isBlank() || motivo.length()>500)) return "VALIDACION";
        cuentas.bloquearCatalogoAdministrador();
        if (cuentas.leer(id).isEmpty()) return "NO_ENCONTRADO";
        Stream.of(actor.sub(),id).distinct().sorted(Comparator.comparing(UUID::toString)).forEach(cuentas::bloquear);
        if (!operaciones.autorizado(actor) || !"ACTIVO".equals(cuentas.leer(actor.sub()).orElseThrow().estadoBase()))
            return denegar(actor, contexto);
        var cuenta=cuentas.leer(id).orElseThrow();
        if (!"ACTIVO".equals(cuenta.estadoBase())) return bloquear ? "CUENTA_NO_DISPONIBLE" : null;
        if (bloquear && (id.equals(actor.sub()) || (operaciones.esAdministrador(id)
                && operaciones.otrosAdministradoresActivos(id)==0))) return "ADMINISTRADOR_PROTEGIDO";
        var ahora=reloj.instant();
        var anterior=persistencia.buscarBloqueo(id).orElse(null);
        if (!bloquear && (anterior==null || !anterior.vigenteEn(ahora))) return null;
        if (bloquear && anterior!=null && anterior.tipo()==TipoBloqueo.MANUAL) return null;
        if (bloquear) operaciones.revocarSesiones(id);
        anterior=persistencia.bloquearBloqueo(id).orElse(null);
        if (bloquear) {
            persistencia.guardarBloqueo(new Bloqueo(id,TipoBloqueo.MANUAL,null,motivo.strip(),ahora,UUID.randomUUID()));
        } else {
            var racha=new RachaFallo(id,UUID.randomUUID(),huellas.claveActualId(),ahora);
            if (persistencia.bloquearRacha(id).isPresent()) persistencia.reiniciarRacha(racha);
            else persistencia.crearRacha(racha);
            if (anterior==null || !persistencia.eliminarBloqueo(id,anterior.revision()))
                throw new IllegalStateException("Revisión de bloqueo cambió");
            cuentas.guardarContadores(id,cuenta.bloqueosSeguidos(),0);
        }
        tokens.invalidarPrevios(id);
        operaciones.registrar(bloquear,cuenta,actor,contexto,ahora);
        return null;
    }
    private String denegar(ActorUsuario actor, ContextoPeticion contexto) {
        operaciones.denegado(actor,contexto,reloj.instant());
        return "SCOPE_INSUFICIENTE";
    }
}
