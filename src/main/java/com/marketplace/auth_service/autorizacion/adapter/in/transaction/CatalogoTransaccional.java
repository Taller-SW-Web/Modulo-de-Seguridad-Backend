package com.marketplace.auth_service.autorizacion.adapter.in.transaction;

import com.marketplace.auth_service.autorizacion.application.dto.PermisoCatalogado;
import com.marketplace.auth_service.autorizacion.application.dto.RolCatalogado;
import com.marketplace.auth_service.autorizacion.application.port.in.CatalogoPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Frontera transaccional del catálogo: solo lectura, una transacción por consulta. */
public class CatalogoTransaccional implements CatalogoPort {

    private final CatalogoPort caso;

    public CatalogoTransaccional(CatalogoPort caso) {
        this.caso = caso;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolCatalogado> roles() {
        return caso.roles();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermisoCatalogado> permisos(String modulo) {
        return caso.permisos(modulo);
    }
}
