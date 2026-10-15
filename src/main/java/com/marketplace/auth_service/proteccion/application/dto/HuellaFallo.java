package com.marketplace.auth_service.proteccion.application.dto;
import java.util.Arrays;
import java.util.Objects;
/** Entrada efímera para calcular el HMAC con la racha persistida, nunca un hash
 * elegido por HTTP. El tag persistido pertenece a domain.model.HuellaFallo. */
public final class HuellaFallo implements AutoCloseable {
    private final byte[] secreto;
    private final String correoIntentado;
    private boolean cerrada;
    public HuellaFallo(byte[] secretoEfimero, String correoIntentado) {
        this.secreto = Objects.requireNonNull(secretoEfimero).clone();
        this.correoIntentado = correoIntentado;
    }
    public byte[] copiarBytes() {
        if (cerrada) throw new IllegalStateException("Material efímero descartado");
        return secreto.clone();
    }
    public String correoIntentado() { return correoIntentado; }
    @Override public void close() { Arrays.fill(secreto, (byte) 0); cerrada = true; }
    @Override public String toString() { return "HuellaFallo[REDACTADO]"; }
}
