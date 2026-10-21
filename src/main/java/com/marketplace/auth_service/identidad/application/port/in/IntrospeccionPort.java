package com.marketplace.auth_service.identidad.application.port.in;

import com.marketplace.auth_service.identidad.application.dto.ResultadoIntrospeccion;
import com.marketplace.auth_service.shared.domain.ActorServicio;

/**
 * Introspección de accesos de persona (SPEC-17 RF-17.6/17.7, ESC-17.5/17.6).
 * El filtro ya exigió al módulo {@code tokens:introspeccion} antes de llegar aquí.
 */
public interface IntrospeccionPort {

    ResultadoIntrospeccion introspeccionar(ActorServicio solicitante, String token);
}
