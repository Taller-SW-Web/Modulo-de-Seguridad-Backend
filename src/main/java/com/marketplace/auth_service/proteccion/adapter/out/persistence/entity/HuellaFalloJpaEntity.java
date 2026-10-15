package com.marketplace.auth_service.proteccion.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity @Table(name = "huella_fallo") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class HuellaFalloJpaEntity {
    @EmbeddedId private HuellaFalloId id;
    @Column(name = "creado_en", nullable = false) private OffsetDateTime creadoEn;
    @Override public String toString() { return "HuellaFalloJpaEntity[id=" + id + "]"; }
}
