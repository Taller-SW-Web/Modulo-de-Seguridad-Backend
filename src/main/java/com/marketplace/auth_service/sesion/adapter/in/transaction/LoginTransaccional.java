package com.marketplace.auth_service.sesion.adapter.in.transaction;

import com.marketplace.auth_service.sesion.application.dto.CredencialesLogin;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.sesion.application.port.in.LoginPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frontera transaccional de {@link LoginPort} (BACKEND-HEXAGONAL §4, REQUIRED):
 * proveedores y apertura comparten el commit; un fallo técnico revierte el
 * conjunto. Es el único bean del puerto.
 */
public class LoginTransaccional implements LoginPort {

    private final LoginPort casoDeUso;

    public LoginTransaccional(LoginPort casoDeUso) {
        this.casoDeUso = casoDeUso;
    }

    @Override
    @Transactional
    public ResultadoLogin ingresar(CredencialesLogin credenciales, ContextoPeticion contexto) {
        return casoDeUso.ingresar(credenciales, contexto);
    }
}
