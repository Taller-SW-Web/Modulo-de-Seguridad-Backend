package com.marketplace.auth_service.credencial.domain.model;

import java.text.Normalizer;
import java.util.Locale;

/** Solo copias para política. No usar en hashing o preverificación. */
public final class ComparacionPassword {
    private ComparacionPassword() { }
    public static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("(?U)\\s+", " ").strip();
    }
}
