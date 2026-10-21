package com.marketplace.auth_service.identidad.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Estado de un acceso de persona <b>en el instante de la consulta</b> (SPEC-17
 * RF-17.6/17.7). Siempre se responde 200: lo utilizable va en {@code activo}.
 */
public sealed interface ResultadoIntrospeccion {

    /** Motivos del contrato ({@code IntrospeccionInactiva.motivo}). */
    enum Motivo { TOKEN_VENCIDO, TOKEN_REVOCADO, FIRMA_INVALIDA, USUARIO_INACTIVO, USUARIO_BLOQUEADO }

    /** Acceso utilizable: roles y permisos son los actuales de la base, no los del token. */
    record Activa(UUID sub, String email, List<String> roles, List<String> permisos, Instant expiraEn)
            implements ResultadoIntrospeccion {
        public Activa {
            Objects.requireNonNull(sub, "sub");
            roles = List.copyOf(roles);
            permisos = List.copyOf(permisos);
        }
    }

    /** Acceso no utilizable; la firma puede ser correcta (ESC-17.6). */
    record Inactiva(Motivo motivo) implements ResultadoIntrospeccion {
        public Inactiva {
            Objects.requireNonNull(motivo, "motivo");
        }
    }
}
