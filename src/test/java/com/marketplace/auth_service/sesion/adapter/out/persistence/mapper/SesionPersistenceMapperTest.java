package com.marketplace.auth_service.sesion.adapter.out.persistence.mapper;

import com.marketplace.auth_service.sesion.domain.model.AccesoSesion;
import com.marketplace.auth_service.sesion.domain.model.SesionFamilia;
import com.marketplace.auth_service.sesion.domain.model.TokenRefresco;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SesionPersistenceMapperTest {
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    void familiaIdaYVueltaConRevocadaEnNulaYFijada() {
        var activa = SesionFamilia.nueva(UUID.randomUUID(), UUID.randomUUID(), AHORA);
        assertThat(SesionPersistenceMapper.aDominio(SesionPersistenceMapper.aEntidad(activa))).isEqualTo(activa);
        var revocada = activa.revocar(AHORA.plusSeconds(30));
        assertThat(SesionPersistenceMapper.aDominio(SesionPersistenceMapper.aEntidad(revocada))).isEqualTo(revocada);
    }

    @Test
    void tokenIdaYVueltaSinExponerHashEnToString() {
        var modelo = TokenRefresco.nuevo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), HASH, AHORA);
        var entidad = SesionPersistenceMapper.aEntidad(modelo);
        assertThat(SesionPersistenceMapper.aDominio(entidad)).isEqualTo(modelo);
        assertThat(entidad.toString()).doesNotContain(HASH);
    }

    @Test
    void accesoIdaYVuelta() {
        var modelo = AccesoSesion.nuevo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AHORA);
        assertThat(SesionPersistenceMapper.aDominio(SesionPersistenceMapper.aEntidad(modelo))).isEqualTo(modelo);
    }
}
