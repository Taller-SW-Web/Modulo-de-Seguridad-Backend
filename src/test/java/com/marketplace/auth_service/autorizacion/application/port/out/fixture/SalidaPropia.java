package com.marketplace.auth_service.autorizacion.application.port.out.fixture;

import com.marketplace.auth_service.autorizacion.domain.fixture.ModeloInterno;

/** Los puertos de salida propios pueden transportar el modelo de su dominio. */
public interface SalidaPropia { void guardar(ModeloInterno valor); }
