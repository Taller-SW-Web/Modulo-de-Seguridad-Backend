package com.marketplace.auth_service.credencial.adapter.out.crypto;

import com.marketplace.auth_service.credencial.application.port.out.HashCorreoSolicitudPort;
import com.marketplace.auth_service.credencial.domain.exception.SolicitudRecuperacionNoDisponibleException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA-256 keyed pseudonym; the clear email is never persisted for quota tracking. */
public final class HmacCorreoSolicitudJca implements HashCorreoSolicitudPort {
    private static final byte[] DOMINIO = "G7-SOLICITUD-LIMITADA-v1:".getBytes(StandardCharsets.US_ASCII);
    private final SecretKeySpec clave;

    public HmacCorreoSolicitudJca(byte[] clave) {
        Objects.requireNonNull(clave, "clave");
        if (clave.length < 32) throw new IllegalArgumentException("La clave HMAC requiere al menos 32 bytes");
        this.clave = new SecretKeySpec(clave.clone(), "HmacSHA256");
    }

    @Override
    public String generar(String correoNormalizado) {
        Objects.requireNonNull(correoNormalizado, "correoNormalizado");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(clave);
            mac.update(DOMINIO);
            return HexFormat.of().formatHex(mac.doFinal(correoNormalizado.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new SolicitudRecuperacionNoDisponibleException();
        }
    }

    @Override public String toString() { return "HmacCorreoSolicitudJca[material=REDACTADO]"; }
}
