package com.marketplace.auth_service.perfil.domain.model;

import java.util.Objects;

/** Enmascarado de documento: cinco asteriscos y los últimos tres caracteres (*****234). */
public final class DocumentoEnmascarado {
    private static final String PREFIJO = "*****";
    private static final int VISIBLES = 3;

    private DocumentoEnmascarado() {
    }

    /** Recibe el número ya descifrado; nunca el texto cifrado. */
    public static String de(String numeroEnClaro) {
        Objects.requireNonNull(numeroEnClaro, "numeroEnClaro");
        String numero = numeroEnClaro.strip();
        if (numero.length() < VISIBLES) {
            throw new IllegalArgumentException("El documento es demasiado corto para enmascararlo");
        }
        return PREFIJO + numero.substring(numero.length() - VISIBLES);
    }
}
