package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import com.marketplace.auth_service.mfa.domain.model.Otp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia propia. Guardados/locks requieren transacción del coordinador y lock previo de usuario. */
public interface PersistenciaMfaPort {
    void guardarDesafio(DesafioMfa desafio);
    Optional<DesafioMfa> buscarDesafioPorHash(String hash);
    Optional<DesafioMfa> bloquearDesafio(UUID id);
    void guardarOtp(Otp otp);
    Optional<Otp> bloquearOtp(UUID id);
    List<Otp> listarCodigosDelDesafio(UUID desafioId);
    long contarSolicitudes(UUID usuarioId, Instant limiteExclusivo);
    Optional<Instant> primeraSolicitudEnVentana(UUID usuarioId, Instant limiteExclusivo);
}
