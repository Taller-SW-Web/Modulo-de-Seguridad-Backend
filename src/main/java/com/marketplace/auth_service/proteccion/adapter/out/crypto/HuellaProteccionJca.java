package com.marketplace.auth_service.proteccion.adapter.out.crypto;

import com.marketplace.auth_service.proteccion.application.port.out.HuellaProteccionPort;
import com.marketplace.auth_service.proteccion.domain.exception.HuellaNoDisponibleException;
import com.marketplace.auth_service.proteccion.domain.model.HuellaFallo;
import com.marketplace.auth_service.proteccion.domain.model.RachaFallo;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** HMAC-SHA256 con clave externa persistente y contexto de racha. */
public final class HuellaProteccionJca implements HuellaProteccionPort {
    private final String actualId;
    private final Map<String, byte[]> claves;

    public HuellaProteccionJca(String actualId, Map<String, byte[]> registro) {
        Objects.requireNonNull(registro);
        Map<String, byte[]> copia = new HashMap<>();
        registro.forEach((id, clave) -> {
            if (id == null || id.isBlank() || id.length() > 50 || !id.equals(id.strip())
                    || clave == null || clave.length < 32) {
                throw new IllegalArgumentException("Configuración de huellas inválida");
            }
            copia.put(id, clave.clone());
        });
        if (!copia.containsKey(actualId)) {
            throw new IllegalArgumentException("Clave actual de huellas no disponible");
        }
        this.actualId = actualId;
        this.claves = Map.copyOf(copia);
    }

    @Override public String claveActualId() { return actualId; }

    @Override public HuellaFallo generar(RachaFallo racha, byte[] secretoEfimero, Instant ahora) {
        Objects.requireNonNull(racha);
        Objects.requireNonNull(secretoEfimero);
        Objects.requireNonNull(ahora);
        byte[] clave = claves.get(racha.hashKeyId());
        if (clave == null) throw new HuellaNoDisponibleException();
        // No normalizar, truncar ni convertir la contraseña a String. Copia efímera.
        byte[] secreto = secretoEfimero.clone();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            campo(mac, "G7-FALLO-v1".getBytes(StandardCharsets.UTF_8));
            campo(mac, "FALLO_LOGIN".getBytes(StandardCharsets.UTF_8));
            campo(mac, racha.usuarioId().toString().getBytes(StandardCharsets.UTF_8));
            campo(mac, racha.rachaId().toString().getBytes(StandardCharsets.UTF_8));
            campo(mac, secreto);
            return new HuellaFallo(racha.usuarioId(), racha.rachaId(),
                    HexFormat.of().formatHex(mac.doFinal()), ahora);
        } catch (GeneralSecurityException e) {
            throw new HuellaNoDisponibleException();
        } finally {
            Arrays.fill(secreto, (byte) 0);
        }
    }

    private static void campo(Mac mac, byte[] valor) {
        mac.update(ByteBuffer.allocate(Integer.BYTES).putInt(valor.length).array());
        mac.update(valor);
    }
    @Override public String toString() { return "HuellaProteccionJca[material=REDACTADO]"; }
}
