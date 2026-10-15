package com.marketplace.auth_service.credencial.adapter.out.config;

import com.marketplace.auth_service.credencial.domain.exception.PoliticaNoDisponibleException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PasswordComunesArchivoTest {
    @TempDir Path directorio;
    @Test void sinPrimeraListaValidaNoPermiteValidar() {
        var lista = new PasswordComunesArchivo(directorio.resolve("ausente.txt"));
        assertThat(lista.version()).isEmpty();
        assertThatThrownBy(() -> lista.contiene("x")).isInstanceOf(PoliticaNoDisponibleException.class);
    }
    @Test void recargaValidaReemplazaCompletamenteYVersiona() throws Exception {
        Path archivo = directorio.resolve("lista.txt");
        Files.writeString(archivo, "Cómún1! A\nOtra2!B\n");
        var lista = new PasswordComunesArchivo(archivo);
        String primera = lista.version().orElseThrow();
        assertThat(lista.contiene("comun1! a")).isTrue();
        Files.writeString(archivo, "Siguiente3!C\n", StandardCharsets.UTF_8);
        lista.recargar();
        assertThat(lista.version().orElseThrow()).isNotEqualTo(primera);
        assertThat(lista.contiene("comun1! a")).isFalse();
        assertThat(lista.contiene("siguiente3!c")).isTrue();
    }
    @Test void falloVacioUTF8InvalidoYArchivoAusenteConservanUltimaVersion() throws Exception {
        Path archivo = directorio.resolve("lista.txt");
        Files.writeString(archivo, "Vigente1!A\n");
        var lista = new PasswordComunesArchivo(archivo);
        String version = lista.version().orElseThrow();
        Files.writeString(archivo, "  \n\t");
        lista.recargar();
        assertThat(lista.version()).contains(version);
        Files.write(archivo, new byte[]{(byte) 0xc3, (byte) 0x28});
        lista.recargar();
        assertThat(lista.version()).contains(version);
        Files.delete(archivo);
        lista.recargar();
        assertThat(lista.contiene("vigente1!a")).isTrue();
        assertThat(lista.version()).contains(version);
    }
    @Test void primeraCargaInvalidaNoPublicaSnapshot() throws Exception {
        Path archivo = directorio.resolve("lista.txt");
        Files.write(archivo, new byte[]{(byte) 0xff});
        var lista = new PasswordComunesArchivo(archivo);
        assertThatThrownBy(() -> lista.contiene("x")).isInstanceOf(PoliticaNoDisponibleException.class);
    }
}
