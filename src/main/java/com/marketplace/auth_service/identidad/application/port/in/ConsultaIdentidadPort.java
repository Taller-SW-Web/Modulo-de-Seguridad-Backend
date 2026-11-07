package com.marketplace.auth_service.identidad.application.port.in;

import com.marketplace.auth_service.identidad.application.dto.ResultadoConsultaIdentidad;
import com.marketplace.auth_service.identidad.application.dto.ResultadoConsultaLoteIdentidad;
import com.marketplace.auth_service.shared.domain.ActorServicio;
import java.util.List;

/**
 * Consulta de identidad para módulos consumidores (SPEC-18 RF-18.1 a RF-18.4).
 * El scope {@code usuarios:leer} ya lo exigió la regla de la ruta; aquí se
 * decide solo si el documento sale en claro o enmascarado, y se audita el acceso.
 */
public interface ConsultaIdentidadPort {

    /** {@code idTexto} llega sin validar: un id malformado es {@code Invalida}, no {@code NoEncontrado}. */
    ResultadoConsultaIdentidad obtener(ActorServicio actor, String idTexto);

    /**
     * Lote de hasta 100 identificadores (SPEC-18 RF-18.2, FEAT-06-T2). El límite y el
     * formato de cada id se validan aquí, antes de consultar nada; un lote demasiado
     * grande o con un id malformado no llega a leer cuentas.
     */
    ResultadoConsultaLoteIdentidad obtenerLote(ActorServicio actor, List<String> idsTexto);
}
