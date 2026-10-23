package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import java.util.Optional;

/** Salida propia y estrecha; los guardados participan en la transacción del coordinador. */
public interface AlmacenDesafiosMfaPort {
    void guardarDesafio(DesafioMfa desafio);
    Optional<DesafioMfa> buscarDesafioPorHash(String hash);
}
