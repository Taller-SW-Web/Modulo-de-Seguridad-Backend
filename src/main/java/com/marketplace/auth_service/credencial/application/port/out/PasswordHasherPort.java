package com.marketplace.auth_service.credencial.application.port.out;

import com.marketplace.auth_service.credencial.domain.model.HashContrasena;

public interface PasswordHasherPort {
    HashContrasena generar(CharSequence secreto);

    boolean verificar(CharSequence secreto, HashContrasena hash);
}
