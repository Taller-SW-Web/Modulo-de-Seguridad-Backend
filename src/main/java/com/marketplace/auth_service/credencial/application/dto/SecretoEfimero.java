package com.marketplace.auth_service.credencial.application.dto;

import java.util.Objects;

/** Se conserva exactamente como se recibió; nunca usar su valor en logs. */
public record SecretoEfimero(String valor) {
    public SecretoEfimero { Objects.requireNonNull(valor, "secreto"); }
    @Override public String toString() { return "SecretoEfimero[REDACTADO]"; }
}
