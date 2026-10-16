package com.marketplace.auth_service.auditoria.adapter.out.crypto;

import com.marketplace.auth_service.auditoria.application.port.out.CifradoMensajesPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM, IV aleatorio de 12 bytes por mensaje, id de clave como AAD; formato {@code kid:base64(iv||ct)}. */
public final class CifradoMensajesAesGcm implements CifradoMensajesPort {
    private static final int IV = 12;
    private static final int TAG_BITS = 128;

    private final String claveId;
    private final SecretKeySpec clave;
    private final SecureRandom azar = new SecureRandom();

    public CifradoMensajesAesGcm(String claveId, byte[] material) {
        if (claveId == null || claveId.isBlank() || claveId.contains(":") || material == null || material.length != 32) {
            throw new IllegalArgumentException("Clave de outbox inválida: id sin ':' y 32 bytes exactos");
        }
        this.claveId = claveId;
        this.clave = new SecretKeySpec(material.clone(), "AES");
    }

    @Override
    public String cifrar(String textoPlano) {
        Objects.requireNonNull(textoPlano);
        try {
            byte[] iv = new byte[IV];
            azar.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(claveId.getBytes(StandardCharsets.UTF_8));
            byte[] ct = c.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            return claveId + ":" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(IV + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el mensaje", e);
        }
    }

    @Override
    public String descifrar(String cifrado) {
        int sep = cifrado == null ? -1 : cifrado.indexOf(':');
        if (sep <= 0 || !cifrado.substring(0, sep).equals(claveId)) {
            throw new IllegalStateException("Mensaje cifrado mal formado o con clave desconocida");
        }
        try {
            byte[] todo = Base64.getDecoder().decode(cifrado.substring(sep + 1));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, todo, 0, IV));
            c.updateAAD(claveId.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(todo, IV, todo.length - IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Mensaje cifrado inválido o alterado", e);
        }
    }
}
