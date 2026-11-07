package com.marketplace.auth_service.mfa.adapter.out.crypto;

import com.marketplace.auth_service.mfa.application.dto.DesafioGenerado;
import com.marketplace.auth_service.mfa.application.dto.OtpGenerado;
import com.marketplace.auth_service.mfa.application.port.out.CriptografiaMfaPort;
import com.marketplace.auth_service.mfa.domain.model.ContextoOtp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** JCA de Java 21: clave HMAC externa; no se genera ni sustituye al reiniciar. */
public final class CriptografiaMfaJca implements CriptografiaMfaPort {
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final HexFormat HEX = HexFormat.of();
    private final SecureRandom random;
    private final String claveActual;
    private final Map<String, SecretKeySpec> claves;

    public CriptografiaMfaJca(String claveActual, Map<String, byte[]> clavesExternas, SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
        Objects.requireNonNull(clavesExternas, "clavesExternas");
        Map<String, SecretKeySpec> copia = new HashMap<>();
        clavesExternas.forEach((id, bytes) -> {
            if (id == null || id.isBlank() || id.length() > 50 || bytes == null || bytes.length < 32) {
                throw new IllegalArgumentException("Cada clave OTP exige identificador y al menos 32 bytes externos");
            }
            copia.put(id, new SecretKeySpec(bytes.clone(), "HmacSHA256"));
        });
        if (claveActual == null || !copia.containsKey(claveActual)) {
            throw new IllegalArgumentException("Falta la clave HMAC actual; generación OTP denegada");
        }
        this.claveActual = claveActual;
        this.claves = Map.copyOf(copia);
    }

    @Override
    public DesafioGenerado generarDesafio() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = BASE64URL.encodeToString(bytes);
        return new DesafioGenerado(token, hashDesafio(token));
    }

    @Override
    public String hashDesafio(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Formato de desafío inválido");
        }
        byte[] bytes = Base64.getUrlDecoder().decode(token);
        if (bytes.length != 32 || !BASE64URL.encodeToString(bytes).equals(token)) {
            throw new IllegalArgumentException("Formato de desafío inválido");
        }
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 no disponible");
        }
    }

    @Override
    public OtpGenerado generarOtp(ContextoOtp contexto) {
        Objects.requireNonNull(contexto, "contexto");
        String codigo = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        return new OtpGenerado(codigo, HEX.formatHex(hmac(contexto, codigo, claves.get(claveActual))), claveActual);
    }

    @Override
    public boolean coincideOtp(ContextoOtp contexto, String codigo, String hashCodigo, String hashKeyId) {
        Objects.requireNonNull(contexto, "contexto");
        SecretKeySpec clave = hashKeyId == null ? null : claves.get(hashKeyId);
        if (clave == null || codigo == null || !codigo.matches("[0-9]{6}")
                || hashCodigo == null || !hashCodigo.matches("[a-f0-9]{64}")) {
            return false;
        }
        byte[] esperado = HEX.parseHex(hashCodigo);
        byte[] recibido = hmac(contexto, codigo, clave);
        return MessageDigest.isEqual(esperado, recibido);
    }

    private static byte[] hmac(ContextoOtp contexto, String codigo, SecretKeySpec clave) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(clave);
            // Versión y campos UTF-8 con longitud de 32 bits: concatenación inequívoca.
            agregarCampo(mac, "G7-OTP-v1");
            agregarCampo(mac, contexto.proposito());
            agregarCampo(mac, contexto.usuarioId().toString());
            agregarCampo(mac, contexto.desafioId().toString());
            agregarCampo(mac, contexto.otpId().toString());
            agregarCampo(mac, codigo);
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo proteger el OTP");
        }
    }

    private static void agregarCampo(Mac mac, String campo) {
        byte[] bytes = campo.getBytes(StandardCharsets.UTF_8);
        mac.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        mac.update(bytes);
    }

    @Override
    public String toString() {
        return "CriptografiaMfaJca[claveActual=" + claveActual + ", material=REDACTADO]";
    }
}
