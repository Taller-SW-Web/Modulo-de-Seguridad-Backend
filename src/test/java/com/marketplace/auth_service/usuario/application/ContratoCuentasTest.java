package com.marketplace.auth_service.usuario.application;
import com.marketplace.auth_service.usuario.application.dto.*;
import com.marketplace.auth_service.usuario.application.port.in.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.marketplace.auth_service.usuario.application.dto.CampoBasico.*;
class ContratoCuentasTest {
    @Test void validaPaginacionYEvitaDesbordamiento() {
        assertThatThrownBy(()->new Paginacion(0,20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Paginacion(1,101)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Paginacion(Integer.MAX_VALUE,100).offset()).isGreaterThan(Integer.MAX_VALUE);
    }
    @Test void noSilenciaFiltrosInvalidos() {
        assertThatThrownBy(()->new FiltroCuenta("DESCONOCIDO",null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new FiltroCuenta(null,"DESCONOCIDO",null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nombresNoAdmitenNullYContadoresNoAdmitenNegativos() {
        assertThatThrownBy(()->new CambioBasicos(presente(null),omitido(),omitido())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ContadoresProteccion(-1,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ContadoresProteccion(0,-1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void paginaEsCopiaInmutable() {
        var origen=new ArrayList<>(List.of("uno"));
        var p=new PaginaResultado<>(origen,1,20,1);
        origen.clear();
        assertThat(p.elementos()).containsExactly("uno");
        assertThatThrownBy(()->p.elementos().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
