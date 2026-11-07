package com.marketplace.auth_service.identidad.application.dto;

import java.util.UUID;

/**
 * Resultado de {@link com.marketplace.auth_service.identidad.application.port.in.ConsultaIdentidadPort#obtener}
 * (SPEC-18): sin excepciones en la frontera exportada, igual que
 * {@code ResultadoTokenServicio} (SPEC-17).
 */
public sealed interface ResultadoConsultaIdentidad {

    record Encontrado(UsuarioServicioVista usuario) implements ResultadoConsultaIdentidad {
    }

    /** Id malformado (SPEC-18 ESC-18.3): 400 {@code VALIDACION}, no 404. */
    record Invalida(String campo, String mensaje) implements ResultadoConsultaIdentidad {
    }

    record NoEncontrado(UUID id) implements ResultadoConsultaIdentidad {
    }
}
