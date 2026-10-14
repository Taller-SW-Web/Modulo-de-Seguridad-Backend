package com.marketplace.auth_service.mfa.domain.model;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class PoliticaMfaTest {
    @Test void cualquierRolDeGestionObligaAunqueLaBanderaSeaFalse() {
        for(String rol:Set.of("ADMIN_VENTAS","GESTOR_DESPACHO","GESTOR_COMERCIAL","ADMIN_SISTEMA")) {
            assertThat(PoliticaMfa.efectivo(false,Set.of(rol))).isTrue();
            assertThat(PoliticaMfa.obligatorio(Set.of(rol))).isTrue();
        }
    }
    @Test void clienteYVendedorSiguenLaBanderaVoluntaria() {
        for(Set<String> roles:java.util.List.of(Set.of("CLIENTE"),Set.of("VENDEDOR"),Set.of("CLIENTE","VENDEDOR"))) {
            assertThat(PoliticaMfa.efectivo(false,roles)).isFalse();
            assertThat(PoliticaMfa.efectivo(true,roles)).isTrue();
            assertThat(PoliticaMfa.obligatorio(roles)).isFalse();
        }
    }
    @Test void rolesMultiplesNoOcultanGestion() {
        assertThat(PoliticaMfa.efectivo(false,Set.of("CLIENTE","VENDEDOR","GESTOR_COMERCIAL"))).isTrue();
    }
    @Test void cuentaSinRolNoObtieneUnRolInventado() {
        assertThat(PoliticaMfa.efectivo(false,Set.of())).isFalse();
        assertThat(PoliticaMfa.efectivo(true,Set.of())).isTrue();
    }
    @Test void rolDesconocidoOMalEscritoNoSeTrataComoOpcional() {
        for(String rol:java.util.List.of("SUPER_ADMIN","admin_sistema","ADMIN_SISTEMA ")) {
            assertThatThrownBy(() -> PoliticaMfa.efectivo(true,Set.of(rol)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void rolesNulosNoHabilitanUnaDecisionDeAcceso() {
        assertThatThrownBy(() -> PoliticaMfa.efectivo(false,null)).isInstanceOf(NullPointerException.class);
    }
}
