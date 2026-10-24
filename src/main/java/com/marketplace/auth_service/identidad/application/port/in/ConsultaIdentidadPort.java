package com.marketplace.auth_service.identidad.application.port.in;

import com.marketplace.auth_service.identidad.application.dto.ResultadoConsultaIdentidad;
import com.marketplace.auth_service.shared.domain.ActorServicio;

/**
 * Consulta de identidad para módulos consumidores (SPEC-18 RF-18.1 a RF-18.4).
 * El scope {@code usuarios:leer} ya lo exigió la regla de la ruta; aquí se
 * decide solo si el documento sale en claro o enmascarado, y se audita el acceso.
 */
public interface ConsultaIdentidadPort {

    /** {@code idTexto} llega sin validar: un id malformado es {@code Invalida}, no {@code NoEncontrado}. */
    ResultadoConsultaIdentidad obtener(ActorServicio actor, String idTexto);
}
