package com.marketplace.auth_service.proteccion.adapter.in.transaction;

import com.marketplace.auth_service.proteccion.application.port.in.BloqueoManualPort;
import com.marketplace.auth_service.shared.domain.*;
import org.springframework.transaction.annotation.*;

public class BloqueoManualTransaccional implements BloqueoManualPort {
    private final BloqueoManualPort delegado;
    public BloqueoManualTransaccional(BloqueoManualPort delegado) { this.delegado=delegado; }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public String ejecutar(ActorUsuario actor,String id,boolean bloquear,String motivo,ContextoPeticion contexto) {
        return delegado.ejecutar(actor,id,bloquear,motivo,contexto);
    }
}
