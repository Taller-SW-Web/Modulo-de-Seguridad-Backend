package com.marketplace.auth_service.proteccion.adapter.out.persistence.mapper;

import com.marketplace.auth_service.proteccion.adapter.out.persistence.entity.*;
import com.marketplace.auth_service.proteccion.domain.model.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class ProteccionPersistenceMapper {
    private ProteccionPersistenceMapper() {}
    private static OffsetDateTime utc(Instant valor) {
        return valor == null ? null : valor.atOffset(ZoneOffset.UTC);
    }
    private static Instant instante(OffsetDateTime valor) {
        return valor == null ? null : valor.toInstant();
    }
    public static BloqueoJpaEntity aEntidad(Bloqueo b) {
        return new BloqueoJpaEntity(b.usuarioId(), b.tipo(), utc(b.bloqueadoHasta()),
                b.motivo(), utc(b.creadoEn()), b.revision());
    }
    public static Bloqueo aDominio(BloqueoJpaEntity b) {
        return new Bloqueo(b.getUsuarioId(), b.getTipo(), instante(b.getBloqueadoHasta()),
                b.getMotivo(), instante(b.getCreadoEn()), b.getRevision());
    }
    public static RachaFalloJpaEntity aEntidad(RachaFallo r) {
        return new RachaFalloJpaEntity(r.usuarioId(), r.rachaId(), r.hashKeyId(), utc(r.creadoEn()));
    }
    public static RachaFallo aDominio(RachaFalloJpaEntity r) {
        return new RachaFallo(r.getUsuarioId(), r.getRachaId(), r.getHashKeyId(), instante(r.getCreadoEn()));
    }
    public static HuellaFalloJpaEntity aEntidad(HuellaFallo h) {
        return new HuellaFalloJpaEntity(new HuellaFalloId(h.usuarioId(), h.rachaId(), h.huellaHmac()), utc(h.creadoEn()));
    }
    public static HuellaFallo aDominio(HuellaFalloJpaEntity h) {
        return new HuellaFallo(h.getId().getUsuarioId(), h.getId().getRachaId(),
                h.getId().getHuellaHmac(), instante(h.getCreadoEn()));
    }
    public static IntentoLoginJpaEntity aEntidad(IntentoLogin i) {
        return new IntentoLoginJpaEntity(i.id(), i.usuarioId(), i.correoIntentado(),
                i.resultado(), i.ip(), i.agente(), utc(i.fecha()));
    }
    public static IntentoLogin aDominio(IntentoLoginJpaEntity i) {
        return new IntentoLogin(i.getId(), i.getUsuarioId(), i.getCorreoIntentado(),
                i.getResultado(), i.getIp(), i.getAgente(), instante(i.getFecha()));
    }
}
