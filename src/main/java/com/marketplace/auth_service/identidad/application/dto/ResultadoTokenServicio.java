package com.marketplace.auth_service.identidad.application.dto;

import java.util.List;

/** Resultado tipado de {@code POST /auth/token}; el controlador lo traduce al contrato. */
public sealed interface ResultadoTokenServicio {

    /** Token emitido con los scopes concedidos, que pueden ser menos de los pedidos. */
    record Emitido(TokenServicioFirmado token, List<String> scopes) implements ResultadoTokenServicio {
        public Emitido {
            scopes = List.copyOf(scopes);
        }
    }

    /** Cliente inexistente, inactivo o secreto incorrecto: un solo motivo hacia fuera (401 CLIENTE_INVALIDO). */
    record ClienteInvalido() implements ResultadoTokenServicio { }

    /** Petición mal formada: falta {@code grant_type} o no es {@code client_credentials}. */
    record Invalida(String campo, String mensaje) implements ResultadoTokenServicio { }
}
