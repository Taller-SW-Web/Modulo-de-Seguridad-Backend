package com.marketplace.auth_service.proteccion.adapter.out.persistence.mapper;

import com.marketplace.auth_service.proteccion.adapter.out.persistence.entity.*;
import com.marketplace.auth_service.proteccion.domain.model.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ProteccionPersistenceMapperTest {
    private final UUID usuario=UUID.randomUUID(), id=UUID.randomUUID();
    private final Instant ahora=Instant.parse("2026-10-14T05:00:00Z");
    @Test void bloqueoConservaRevisionHastaYUtc() {
        var b=new Bloqueo(usuario, TipoBloqueo.AUTOMATICO, ahora.plusSeconds(60), null, ahora, id);
        var e=ProteccionPersistenceMapper.aEntidad(b);
        assertThat(e.getCreadoEn().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(ProteccionPersistenceMapper.aDominio(e)).isEqualTo(b);
    }
    @Test void manualSinHastaYRevisionObligatoria() {
        var b=new Bloqueo(usuario,TipoBloqueo.MANUAL,null,"Soporte",ahora,id);
        assertThat(ProteccionPersistenceMapper.aDominio(ProteccionPersistenceMapper.aEntidad(b))).isEqualTo(b);
        assertThatThrownBy(() -> ProteccionPersistenceMapper.aDominio(
                new BloqueoJpaEntity(usuario,TipoBloqueo.AUTOMATICO,null,null,ahora.atOffset(ZoneOffset.UTC),null)))
                .isInstanceOf(NullPointerException.class);
    }
    @Test void rachaYHuellaConservanIdentidadYKeyIdPeroNoExponenHash() {
        var r=new RachaFallo(usuario,id,"k1",ahora);
        var h=new HuellaFallo(usuario,id,"a".repeat(64),ahora);
        assertThat(ProteccionPersistenceMapper.aDominio(ProteccionPersistenceMapper.aEntidad(r))).isEqualTo(r);
        var e=ProteccionPersistenceMapper.aEntidad(h);
        assertThat(ProteccionPersistenceMapper.aDominio(e)).isEqualTo(h);
        assertThat(e.toString()).doesNotContain(h.huellaHmac());
        assertThat(e.getId().toString()).doesNotContain(h.huellaHmac());
    }
    @Test void intentoMapeaTodosLosCamposYUsuarioNullable() {
        var i=new IntentoLogin(id,null,"noexiste@ejemplo.test",ResultadoLogin.FALLO,"::1","Prueba",ahora);
        assertThat(ProteccionPersistenceMapper.aDominio(ProteccionPersistenceMapper.aEntidad(i))).isEqualTo(i);
        assertThat(ProteccionPersistenceMapper.aEntidad(i).toString()).doesNotContain(i.correoIntentado(),"::1","Prueba");
    }
    @Test void instanteConOtroOffsetSeNormalizaSinPerderPrecision() {
        var fecha=OffsetDateTime.parse("2026-10-14T00:00:00.123456-05:00");
        var r=ProteccionPersistenceMapper.aDominio(new RachaFalloJpaEntity(usuario,id,"k1",fecha));
        assertThat(r.creadoEn()).isEqualTo(fecha.toInstant());
        assertThat(ProteccionPersistenceMapper.aEntidad(r).getCreadoEn().getOffset()).isEqualTo(ZoneOffset.UTC);
    }
}
