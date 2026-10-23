package com.marketplace.auth_service.proteccion.adapter.out.crypto;
import com.marketplace.auth_service.proteccion.application.dto.PruebaEnlace;
import com.marketplace.auth_service.proteccion.application.port.out.HashEnlaceProteccionPort;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
public final class HashEnlaceProteccionJca implements HashEnlaceProteccionPort {
    @Override public String calcular(PruebaEnlace prueba) {
        byte[] bytes = prueba.valor().getBytes(StandardCharsets.UTF_8);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException imposible) { throw new IllegalStateException("SHA-256 no disponible", imposible); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }
}
