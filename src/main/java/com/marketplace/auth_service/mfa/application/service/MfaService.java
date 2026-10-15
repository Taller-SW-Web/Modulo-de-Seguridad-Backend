package com.marketplace.auth_service.mfa.application.service;

import com.marketplace.auth_service.mfa.application.dto.*;
import com.marketplace.auth_service.mfa.application.port.in.MfaPort;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import com.marketplace.auth_service.mfa.domain.model.PoliticaMfa;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.UUID;
import java.util.function.Supplier;

/** Núcleo Java puro. No autentica contraseñas, envía OTP ni abre/resetea sesiones. */
public final class MfaService implements MfaPort {
    private final CuentasMfaPort cuentas;
    private final RolesMfaPort roles;
    private final RevisionCredencialMfaPort credenciales;
    private final AlmacenDesafiosMfaPort desafios;
    private final CriptografiaDesafiosMfaPort criptografia;
    private final Clock reloj;
    private final Supplier<UUID> identificadores;

    public MfaService(CuentasMfaPort cuentas, RolesMfaPort roles, RevisionCredencialMfaPort credenciales,
                      AlmacenDesafiosMfaPort desafios, CriptografiaDesafiosMfaPort criptografia,
                      Clock reloj, Supplier<UUID> identificadores) {
        this.cuentas = Objects.requireNonNull(cuentas);
        this.roles = Objects.requireNonNull(roles);
        this.credenciales = Objects.requireNonNull(credenciales);
        this.desafios = Objects.requireNonNull(desafios);
        this.criptografia = Objects.requireNonNull(criptografia);
        this.reloj = Objects.requireNonNull(reloj);
        this.identificadores = Objects.requireNonNull(identificadores);
    }

    @Override public boolean esEfectivo(boolean voluntario, Set<Rol> rolesActuales) {
        return PoliticaMfa.efectivo(voluntario, rolesActuales);
    }

    @Override public ResultadoCreacionDesafio crearDesafio(UUID usuarioId, RevisionCredencialMfa revision) {
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(revision);
        if (!usuarioId.equals(revision.usuarioId())) return ResultadoCreacionDesafio.Rechazado.INVALIDO;
        var cuenta = cuentas.leer(usuarioId);
        if (cuenta.isEmpty() || !cuenta.get().usuarioId().equals(usuarioId)
                || !cuenta.get().disponible() || !cuenta.get().correoVerificado()) {
            return ResultadoCreacionDesafio.Rechazado.INVALIDO;
        }
        if (!credenciales.confirmar(revision)) return ResultadoCreacionDesafio.Rechazado.INVALIDO;
        Set<Rol> actuales = Set.copyOf(roles.leer(usuarioId));
        boolean obligatorio = PoliticaMfa.obligatorio(actuales);
        if (!PoliticaMfa.efectivo(cuenta.get().mfaVoluntario(), actuales)) {
            return new ResultadoCreacionDesafio.NoRequerido(new EstadoMfa(false, obligatorio));
        }

        var generado = criptografia.generarDesafio();
        var desafio = DesafioMfa.nuevo(identificadores.get(), usuarioId, revision.credencialId(),
                revision.version(), generado.hashToken(), reloj.instant());
        desafios.guardarDesafio(desafio);
        // El coordinador del login solo entrega HTTP después de confirmar su transacción.
        return new ResultadoCreacionDesafio.Creado(new DesafioLogin(generado.token(), CanalMfa.EMAIL,
                Math.toIntExact(DesafioMfa.VIGENCIA.toSeconds())));
    }

    @Override public Optional<EstadoMfa> leerEstado(UUID usuarioId, Set<Rol> rolesActuales) {
        Objects.requireNonNull(usuarioId);
        var cuenta = cuentas.leer(usuarioId);
        if (cuenta.isEmpty() || !cuenta.get().usuarioId().equals(usuarioId)) return Optional.empty();
        Set<Rol> actuales = Set.copyOf(rolesActuales);
        boolean obligatorio = PoliticaMfa.obligatorio(actuales);
        return Optional.of(new EstadoMfa(PoliticaMfa.efectivo(cuenta.get().mfaVoluntario(), actuales), obligatorio));
    }
}
