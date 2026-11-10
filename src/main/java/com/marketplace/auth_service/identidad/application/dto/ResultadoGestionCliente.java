package com.marketplace.auth_service.identidad.application.dto;

import java.util.Objects;

/**
 * Resultado de una operación de gestión de clientes técnicos (SPEC-19). Un
 * rechazo lleva el {@code code} publicado y, si es de validación, el campo.
 */
public sealed interface ResultadoGestionCliente<T>
        permits ResultadoGestionCliente.Correcto, ResultadoGestionCliente.Rechazado {

    String VALIDACION = "VALIDACION";
    String NO_ENCONTRADO = "NO_ENCONTRADO";
    String SCOPE_INSUFICIENTE = "SCOPE_INSUFICIENTE";
    String CLIENTE_SERVICIO_EXISTENTE = "CLIENTE_SERVICIO_EXISTENTE";

    record Correcto<T>(T valor) implements ResultadoGestionCliente<T> {
    }

    record Rechazado<T>(String codigo, String campo) implements ResultadoGestionCliente<T> {
        public Rechazado {
            Objects.requireNonNull(codigo, "codigo");
        }
    }

    static <T> ResultadoGestionCliente<T> correcto(T valor) {
        return new Correcto<>(valor);
    }

    static <T> ResultadoGestionCliente<T> rechazo(String codigo) {
        return new Rechazado<>(codigo, null);
    }

    static <T> ResultadoGestionCliente<T> invalido(String campo) {
        return new Rechazado<>(VALIDACION, campo);
    }
}
