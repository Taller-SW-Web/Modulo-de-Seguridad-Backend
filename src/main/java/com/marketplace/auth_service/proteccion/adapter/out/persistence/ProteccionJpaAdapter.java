package com.marketplace.auth_service.proteccion.adapter.out.persistence;

import com.marketplace.auth_service.proteccion.adapter.out.persistence.mapper.ProteccionPersistenceMapper;
import com.marketplace.auth_service.proteccion.adapter.out.persistence.repository.*;
import com.marketplace.auth_service.proteccion.application.port.out.PersistenciaProteccionPort;
import com.marketplace.auth_service.proteccion.domain.model.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class ProteccionJpaAdapter implements PersistenciaProteccionPort {
    private final RachaFalloJpaRepository rachas;
    private final HuellaFalloJpaRepository huellas;
    private final BloqueoJpaRepository bloqueos;
    private final IntentoLoginJpaRepository intentos;

    public ProteccionJpaAdapter(RachaFalloJpaRepository rachas, HuellaFalloJpaRepository huellas,
                                BloqueoJpaRepository bloqueos, IntentoLoginJpaRepository intentos) {
        this.rachas = rachas; this.huellas = huellas; this.bloqueos = bloqueos; this.intentos = intentos;
    }
    @Override public Optional<RachaFallo> buscarRacha(UUID usuario) {
        return rachas.findById(usuario).map(ProteccionPersistenceMapper::aDominio);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RachaFallo> bloquearRacha(UUID usuario) {
        return rachas.bloquear(usuario).map(ProteccionPersistenceMapper::aDominio);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void crearRacha(RachaFallo r) {
        rachas.insertar(r.usuarioId(), r.rachaId(), r.hashKeyId(), r.creadoEn().atOffset(ZoneOffset.UTC));
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void reiniciarRacha(RachaFallo nueva) {
        var anterior = rachas.bloquear(nueva.usuarioId()).orElseThrow(
                () -> new IllegalStateException("No existe racha para reiniciar"));
        if (anterior.getRachaId().equals(nueva.rachaId())) {
            throw new IllegalArgumentException("El reinicio exige una nueva racha");
        }
        huellas.borrarDelUsuario(nueva.usuarioId());
        rachas.saveAndFlush(ProteccionPersistenceMapper.aEntidad(nueva));
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public boolean insertarHuellaSiNueva(HuellaFallo h) {
        return huellas.insertarSiNueva(h.usuarioId(), h.rachaId(), h.huellaHmac(),
                h.creadoEn().atOffset(ZoneOffset.UTC)) == 1;
    }
    @Override public long contarHuellas(UUID usuario, UUID racha) { return huellas.contar(usuario, racha); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void registrarIntento(IntentoLogin i) {
        intentos.insertar(i.id(), i.usuarioId(), i.correoIntentado(), i.resultado().name(),
                i.ip(), i.agente(), i.fecha().atOffset(ZoneOffset.UTC));
    }
    @Override public Optional<Bloqueo> buscarBloqueo(UUID usuario) {
        return bloqueos.leer(usuario).map(ProteccionPersistenceMapper::aDominio);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Bloqueo> bloquearBloqueo(UUID usuario) {
        return bloqueos.bloquear(usuario).map(ProteccionPersistenceMapper::aDominio);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void guardarBloqueo(Bloqueo b) {
        bloqueos.saveAndFlush(ProteccionPersistenceMapper.aEntidad(b));
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public boolean eliminarBloqueo(UUID usuario, UUID revision) {
        return bloqueos.borrarRevision(usuario, revision) == 1;
    }
}
