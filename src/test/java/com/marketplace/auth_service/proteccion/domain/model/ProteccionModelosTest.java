package com.marketplace.auth_service.proteccion.domain.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ProteccionModelosTest {
    private final UUID usuario = UUID.randomUUID(), racha = UUID.randomUUID();
    private final Instant ahora = Instant.parse("2026-10-14T05:00:00Z");

    @Test void automaticoVenceExactamenteEnHastaYNoRequiereMutacion() {
        var bloqueo = new Bloqueo(usuario, TipoBloqueo.AUTOMATICO, ahora.plusSeconds(60), null, ahora, UUID.randomUUID());
        assertThat(bloqueo.vigenteEn(ahora.plusSeconds(59))).isTrue();
        assertThat(bloqueo.vigenteEn(ahora.plusSeconds(60))).isFalse();
        assertThat(bloqueo.vigenteEn(ahora.plusSeconds(600))).isFalse();
    }
    @Test void automaticoSinHastaYManualNoVencen() {
        assertThat(new Bloqueo(usuario, TipoBloqueo.AUTOMATICO, null, null, ahora, UUID.randomUUID())
                .vigenteEn(ahora.plusSeconds(100000))).isTrue();
        assertThat(new Bloqueo(usuario, TipoBloqueo.MANUAL, null, "Soporte", ahora, UUID.randomUUID())
                .vigenteEn(ahora.plusSeconds(100000))).isTrue();
    }
    @Test void noAceptaCombinacionesInvalidasDeTipoMotivoYFecha() {
        assertThatThrownBy(() -> new Bloqueo(usuario, TipoBloqueo.MANUAL, ahora.plusSeconds(60), "Soporte", ahora, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Bloqueo(usuario, TipoBloqueo.MANUAL, null, " ", ahora, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Bloqueo(usuario, TipoBloqueo.AUTOMATICO, ahora, null, ahora, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Bloqueo(usuario, TipoBloqueo.AUTOMATICO, null, "manual", ahora, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void huellaExigeTagHexadecimalDe32Bytes() {
        assertThatThrownBy(() -> new HuellaFallo(usuario, racha, "contraseña", ahora)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HuellaFallo(usuario, racha, "A".repeat(64), ahora)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new HuellaFallo(usuario, racha, "a".repeat(64), ahora).huellaHmac()).hasSize(64);
    }
    @Test void rachaExigeKeyIdPersistenteSinEspaciosYSinVentanaDeTiempo() {
        assertThatThrownBy(() -> new RachaFallo(usuario, racha, " ", ahora)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RachaFallo(usuario, racha, " clave ", ahora)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RachaFallo(usuario, racha, "k".repeat(51), ahora)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new RachaFallo(usuario, racha, "pepper-local", ahora.minusSeconds(900000)).rachaId()).isEqualTo(racha);
    }
    @Test void intentoPermiteUsuarioInexistenteYRechazaMetadatosFueraDeColumna() {
        assertThat(new IntentoLogin(UUID.randomUUID(), null, "noexiste@ejemplo.test", ResultadoLogin.FALLO, null, null, ahora).usuarioId()).isNull();
        assertThatThrownBy(() -> new IntentoLogin(UUID.randomUUID(), usuario, null, ResultadoLogin.FALLO, null, "a".repeat(256), ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void representacionesNoExponenHuellaNiDatosDePeticionOMotivo() {
        assertThat(new HuellaFallo(usuario, racha, "a".repeat(64), ahora).toString()).doesNotContain("a".repeat(64));
        assertThat(new IntentoLogin(UUID.randomUUID(), usuario, "correo-privado", ResultadoLogin.FALLO, "ip-privada", "agente-privado", ahora).toString())
                .doesNotContain("correo-privado", "ip-privada", "agente-privado");
        assertThat(new Bloqueo(usuario, TipoBloqueo.MANUAL, null, "motivo-privado", ahora, UUID.randomUUID()).toString())
                .doesNotContain("motivo-privado");
    }
}
