package com.marketplace.auth_service.sesion.adapter.out.capacidad.fixture;

import com.marketplace.auth_service.autorizacion.domain.model.PermisosEfectivos;

/** SOLO PRUEBA, INVÁLIDO A PROPÓSITO: un puente que usa el modelo interno de autorización. */
public class PuenteConModeloInterno {

    public int contar(PermisosEfectivos interno) {
        return interno.permisos().size();
    }
}
