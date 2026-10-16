package com.marketplace.auth_service.perfil.domain.model;

import java.util.Objects;

/**
 * Documento tal como se guarda en perfil_cliente: cifrado, con la clave que lo
 * cifró y su HMAC. Los cuatro datos van juntos (ck_perfil_cliente_documento).
 * No contiene el número en claro y nunca lo muestra en toString.
 */
public final class DocumentoProtegido {
    private final TipoDocumento tipo;
    private final String cifrado;
    private final String keyId;
    private final String hmac;

    public DocumentoProtegido(TipoDocumento tipo, String cifrado, String keyId, String hmac) {
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.cifrado = noVacio(cifrado, "cifrado");
        this.keyId = noVacio(keyId, "keyId");
        this.hmac = noVacio(hmac, "hmac");
    }

    private static String noVacio(String valor, String campo) {
        Objects.requireNonNull(valor, campo);
        if (valor.isBlank()) {
            throw new IllegalArgumentException("El campo " + campo + " del documento no puede estar vacío");
        }
        return valor;
    }

    public TipoDocumento tipo() {
        return tipo;
    }

    public String cifrado() {
        return cifrado;
    }

    public String keyId() {
        return keyId;
    }

    public String hmac() {
        return hmac;
    }

    @Override
    public String toString() {
        return "DocumentoProtegido[tipo=" + tipo + ", REDACTADO]";
    }
}
