package com.marketplace.auth_service.credencial.adapter.out.config;

import com.marketplace.auth_service.credencial.application.port.out.PasswordComunesPort;
import com.marketplace.auth_service.credencial.domain.exception.PoliticaNoDisponibleException;
import com.marketplace.auth_service.credencial.domain.model.ComparacionPassword;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/** Un snapshot inmutable completo por recarga; nunca se publica una carga parcial. */
public final class PasswordComunesArchivo implements PasswordComunesPort {
    private final Path archivo;
    private volatile Snapshot actual;
    private record Snapshot(Set<String> palabras, String version) { }

    public PasswordComunesArchivo(Path archivo) {
        this.archivo = archivo;
        recargar();
    }
    public synchronized void recargar() {
        try {
            byte[] bytes = Files.readAllBytes(archivo);
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            Set<String> palabras = texto.lines()
                    .map(ComparacionPassword::normalizar).filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
            if (palabras.isEmpty()) return;
            String version = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            actual = new Snapshot(palabras, version);
        } catch (IOException exception) {
            // Se conserva la última lista válida. Ningún contenido se registra.
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no disponible", impossible);
        }
    }
    @Override public boolean contiene(String copiaNormalizada) {
        Snapshot snapshot = actual;
        if (snapshot == null) throw new PoliticaNoDisponibleException();
        return snapshot.palabras().contains(copiaNormalizada);
    }
    /** Identifica la carga efectiva sin exponer su contenido. */
    public java.util.Optional<String> version() {
        Snapshot snapshot = actual;
        return snapshot == null ? java.util.Optional.empty() : java.util.Optional.of(snapshot.version());
    }
}
