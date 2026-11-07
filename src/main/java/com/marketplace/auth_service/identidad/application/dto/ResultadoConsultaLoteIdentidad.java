package com.marketplace.auth_service.identidad.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de {@link com.marketplace.auth_service.identidad.application.port.in.ConsultaIdentidadPort#obtenerLote}
 * (SPEC-18 RF-18.2, ESC-18.2, ESC-18.3): sin excepciones en la frontera exportada,
 * igual que {@link ResultadoConsultaIdentidad}.
 */
public sealed interface ResultadoConsultaLoteIdentidad {

    /** Encontrados y no hallados por separado (ESC-18.2): la petición no falla entera por los que faltan. */
    record Resultado(List<UsuarioServicioVista> usuarios, List<UUID> noEncontrados)
            implements ResultadoConsultaLoteIdentidad {
    }

    /** Un identificador no es un UUID (ESC-18.3): 400 {@code VALIDACION}, el campo es {@code ids[n]}. */
    record Invalida(String campo, String mensaje) implements ResultadoConsultaLoteIdentidad {
    }

    /** Más de 100 identificadores (ESC-18.3): 400 {@code LOTE_DEMASIADO_GRANDE}, nunca truncado en silencio. */
    record LoteDemasiadoGrande(int recibidos, int maximo) implements ResultadoConsultaLoteIdentidad {
    }
}
