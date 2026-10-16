package com.marketplace.auth_service.proteccion.application.dto;
import java.util.Objects;
public record PruebaEnlace(String valor) {
    public PruebaEnlace { Objects.requireNonNull(valor); }
    @Override public String toString() { return "PruebaEnlace[REDACTADO]"; }
}
