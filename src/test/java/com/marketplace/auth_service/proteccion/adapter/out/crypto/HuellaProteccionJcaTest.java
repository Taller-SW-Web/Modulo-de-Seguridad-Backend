package com.marketplace.auth_service.proteccion.adapter.out.crypto;

import com.marketplace.auth_service.proteccion.domain.model.RachaFallo;
import com.marketplace.auth_service.proteccion.domain.exception.HuellaNoDisponibleException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class HuellaProteccionJcaTest {
    private final UUID usuario = UUID.randomUUID(), id = UUID.randomUUID();
    private final Instant ahora = Instant.parse("2026-10-14T05:00:00Z");
    private byte[] clave(int n) { byte[] v = new byte[32]; Arrays.fill(v, (byte)n); return v; }
    private RachaFallo racha(String keyId) { return new RachaFallo(usuario, id, keyId, ahora); }
    private byte[] secreto(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private HuellaProteccionJca proveedor() { return new HuellaProteccionJca("k1", Map.of("k1", clave(7))); }

    @Test void repeticionYReinicioConservanTagSinDependerDeFecha() {
        var primero = proveedor().generar(racha("k1"), secreto("fallo-1"), ahora);
        var segundo = proveedor().generar(racha("k1"), secreto("fallo-1"), ahora.plusSeconds(900000));
        assertThat(primero.huellaHmac()).isEqualTo(segundo.huellaHmac()).hasSize(64);
        assertThat(segundo.creadoEn()).isNotEqualTo(primero.creadoEn());
    }
    @Test void cambiaPorUsuarioRachaOBytesExactosDeLaContrasena() {
        var p = proveedor();
        String original = p.generar(racha("k1"), secreto("incorrecta"), ahora).huellaHmac();
        assertThat(p.generar(new RachaFallo(UUID.randomUUID(), id, "k1", ahora), secreto("incorrecta"), ahora).huellaHmac()).isNotEqualTo(original);
        assertThat(p.generar(new RachaFallo(usuario, UUID.randomUUID(), "k1", ahora), secreto("incorrecta"), ahora).huellaHmac()).isNotEqualTo(original);
        for (String s : List.of("Incorrecta", "incorrecta ", " incorrecta", "incorrectá")) {
            assertThat(p.generar(racha("k1"), secreto(s), ahora).huellaHmac()).isNotEqualTo(original);
        }
    }
    @Test void bytesNoUtf8YNulosNoSeNormalizanNiModificanEnElLlamador() {
        byte[] s = {(byte)0xff, 0, 1};
        byte[] copia = s.clone();
        var p = proveedor();
        String tag = p.generar(racha("k1"), s, ahora).huellaHmac();
        assertThat(s).containsExactly(copia);
        assertThat(p.generar(racha("k1"), new byte[]{(byte)0xfe,0,1}, ahora).huellaHmac()).isNotEqualTo(tag);
    }
    @Test void conservaClaveAnteriorDeUnaRachaDuranteCambioDeClaveActual() {
        var antes = proveedor().generar(racha("k1"), secreto("fallo"), ahora);
        var despues = new HuellaProteccionJca("k2", Map.of("k1", clave(7), "k2", clave(8)));
        assertThat(despues.claveActualId()).isEqualTo("k2");
        assertThat(despues.generar(racha("k1"), secreto("fallo"), ahora).huellaHmac()).isEqualTo(antes.huellaHmac());
        assertThat(despues.generar(racha("k2"), secreto("fallo"), ahora).huellaHmac()).isNotEqualTo(antes.huellaHmac());
    }
    @Test void claveDesconocidaDeniegaSinFabricarUnaRachaNueva() {
        assertThatThrownBy(() -> proveedor().generar(racha("ausente"), secreto("dato-privado"), ahora))
                .isInstanceOf(HuellaNoDisponibleException.class).hasMessageNotContaining("dato-privado");
    }
    @Test void copiaMaterialParaEvitarQueUnaMutacionExternaCambieLaHuella() {
        byte[] externa = clave(7);
        var p = new HuellaProteccionJca("k1", Map.of("k1", externa));
        var original = p.generar(racha("k1"), secreto("fallo"), ahora);
        Arrays.fill(externa, (byte)0);
        assertThat(p.generar(racha("k1"), secreto("fallo"), ahora)).isEqualTo(original);
    }
    @Test void exigePepperDe32BytesYClaveActualDisponible() {
        assertThatThrownBy(() -> new HuellaProteccionJca("k1", Map.of("k1", new byte[31]))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HuellaProteccionJca("k1", Map.of("k2", clave(7)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HuellaProteccionJca("k1", Map.of(" ", clave(7)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void toStringNoContieneClavesNiHuella() {
        var p = proveedor();
        assertThat(p.toString()).doesNotContain(Base64.getEncoder().encodeToString(clave(7)));
        assertThat(p.generar(racha("k1"), secreto("dato-privado"), ahora).toString()).doesNotContain("dato-privado");
    }
    @Test void vectorExternoConfirmaFormatoLongitudPrefijadaYHmac() {
        // Vector calculado independientemente con HMACSHA256 de .NET.
        byte[] key=new byte[32];
        for(int i=0;i<32;i++) key[i]=(byte)i;
        var r=new RachaFallo(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),"vector",Instant.EPOCH);
        var p=new HuellaProteccionJca("vector",Map.of("vector",key));
        assertThat(p.generar(r,secreto("prueba"),Instant.EPOCH).huellaHmac())
                .isEqualTo("90151ecbc0eef7161bc35e19b18b0a6110af5095fc0460a76d24482a227d11f2");
    }
}
