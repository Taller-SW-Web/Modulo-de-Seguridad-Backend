package com.marketplace.auth_service.mfa.domain.model;

import com.marketplace.auth_service.shared.domain.Rol;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static com.marketplace.auth_service.shared.domain.Rol.*;
import static org.assertj.core.api.Assertions.*;

class PoliticaMfaTest {
    @Test void cualquierRolDeGestionObligaAunqueLaBanderaSeaFalse() {
        for (Rol rol : Rol.values()) if (rol.esDeGestion()) {
            assertThat(PoliticaMfa.efectivo(false, Set.of(rol))).isTrue();
            assertThat(PoliticaMfa.obligatorio(Set.of(rol))).isTrue();
        }
    }
    @Test void clienteYVendedorSiguenLaBanderaVoluntaria() {
        for (Set<Rol> roles : java.util.List.of(Set.of(CLIENTE), Set.of(VENDEDOR), Set.of(CLIENTE, VENDEDOR))) {
            assertThat(PoliticaMfa.efectivo(false, roles)).isFalse();
            assertThat(PoliticaMfa.efectivo(true, roles)).isTrue();
            assertThat(PoliticaMfa.obligatorio(roles)).isFalse();
        }
    }
    @Test void rolesMultiplesNoOcultanGestion() {
        assertThat(PoliticaMfa.efectivo(false, Set.of(CLIENTE, VENDEDOR, GESTOR_COMERCIAL))).isTrue();
    }
    @Test void cuentaSinRolNoObtieneUnRolInventado() {
        assertThat(PoliticaMfa.efectivo(false, Set.of())).isFalse();
        assertThat(PoliticaMfa.efectivo(true, Set.of())).isTrue();
    }
    @Test void unRolNuloNoSeTrataComoOpcional() {
        var roles = new java.util.HashSet<Rol>(); roles.add(null);
        assertThatThrownBy(() -> PoliticaMfa.efectivo(true, roles)).isInstanceOf(NullPointerException.class);
    }
    @Test void rolesNulosNoHabilitanUnaDecisionDeAcceso() {
        assertThatThrownBy(() -> PoliticaMfa.efectivo(false, null)).isInstanceOf(NullPointerException.class);
    }
}
