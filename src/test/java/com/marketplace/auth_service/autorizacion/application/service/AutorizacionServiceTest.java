package com.marketplace.auth_service.autorizacion.application.service;

import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.application.port.out.AsociacionesRolPort;
import com.marketplace.auth_service.shared.domain.Rol;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Caso de uso de resolución de permisos, con un doble del puerto de persistencia. */
class AutorizacionServiceTest {

    private final Map<UUID, Set<Rol>> roles = new HashMap<>();
    private final Map<UUID, Set<String>> permisos = new HashMap<>();
    private int consultasDePermisos;

    private final AsociacionesRolPort doble = new AsociacionesRolPort() {
        @Override
        public Set<Rol> rolesDe(UUID usuarioId) {
            return roles.getOrDefault(usuarioId, Set.of());
        }

        @Override
        public Set<String> permisosDe(UUID usuarioId) {
            consultasDePermisos++;
            return permisos.getOrDefault(usuarioId, Set.of());
        }

        @Override
        public Map<Rol, Set<String>> matrizPersistida() {
            return Map.of();
        }
    };

    private final AutorizacionService servicio = new AutorizacionService(doble);

    @Test
    void convierteLoQueDiceLaBaseEnRolesYPermisos() {
        UUID id = UUID.randomUUID();
        roles.put(id, Set.of(Rol.ADMIN_SISTEMA));
        permisos.put(id, Set.of("rol.asignar", "usuario.ver"));

        PermisosResueltos efectivos = servicio.resolver(id);

        assertThat(efectivos.codigosRoles()).containsExactly("ADMIN_SISTEMA");
        assertThat(efectivos.codigosPermisos()).containsExactly("rol.asignar", "usuario.ver");
    }

    @Test
    void sinRolesNoConsultaPermisos() {
        PermisosResueltos efectivos = servicio.resolver(UUID.randomUUID());

        assertThat(efectivos).isEqualTo(PermisosResueltos.ninguno());
        assertThat(consultasDePermisos).isZero();
    }

    @Test
    void unPermisoFueraDelCatalogoDetieneLaResolucion() {
        UUID id = UUID.randomUUID();
        roles.put(id, Set.of(Rol.CLIENTE));
        permisos.put(id, Set.of("pedido.crear"));

        assertThatThrownBy(() -> servicio.resolver(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pedido.crear");
    }

    @Test
    void elUsuarioEsObligatorio() {
        assertThatThrownBy(() -> servicio.resolver(null)).isInstanceOf(NullPointerException.class);
    }
}
