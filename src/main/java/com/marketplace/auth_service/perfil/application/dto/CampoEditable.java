package com.marketplace.auth_service.perfil.application.dto;

/** Omitido conserva el dato; presente distingue null de valor (PATCH con presencia explícita). */
public record CampoEditable<T>(boolean presente, T valor) {
    public CampoEditable {
        if (!presente && valor != null) {
            throw new IllegalArgumentException("Campo omitido con valor");
        }
    }

    public static <T> CampoEditable<T> omitido() {
        return new CampoEditable<>(false, null);
    }

    public static <T> CampoEditable<T> presente(T valor) {
        return new CampoEditable<>(true, valor);
    }

    /** Sin el valor: puede ser un dato personal. */
    @Override
    public String toString() {
        return "CampoEditable[presente=" + presente + "]";
    }
}
