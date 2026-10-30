package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.credencial.application.dto.RevisionCredencial;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.perfil.application.port.out.CredencialesCorreoPort;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Puente a {@link CredencialesPort} (Juan José): la comparación y la revisión viven allí. */
public final class CredencialesCorreoAdapter implements CredencialesCorreoPort {

    private record Revision(RevisionCredencial valor) implements RevisionContrasena {}

    private final CredencialesPort credenciales;

    public CredencialesCorreoAdapter(CredencialesPort credenciales) {
        this.credenciales = Objects.requireNonNull(credenciales);
    }

    @Override
    public Optional<RevisionContrasena> preverificar(UUID usuarioId, String contrasenaActual) {
        var previa = credenciales.preverificar(Optional.of(usuarioId), new SecretoEfimero(contrasenaActual));
        if (!previa.coincide() || previa.revision().isEmpty()) return Optional.empty();
        return Optional.of(new Revision(previa.revision().get()));
    }

    @Override
    public boolean confirmarRevision(RevisionContrasena revision) {
        if (!(revision instanceof Revision propia)) {
            throw new IllegalArgumentException("Revisión de otra procedencia");
        }
        return credenciales.confirmarRevision(propia.valor());
    }
}
