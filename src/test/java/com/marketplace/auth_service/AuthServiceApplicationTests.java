package com.marketplace.auth_service;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentaEscrituraInterna;
import com.marketplace.auth_service.usuario.application.service.ConsultaCuentasService;
import static org.assertj.core.api.Assertions.assertThat;
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
class AuthServiceApplicationTests {
    @Autowired ApplicationContext contexto;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping rutas;
    @Test void contextLoads() {
        assertThat(contexto.getBeansOfType(CuentasPort.class)).hasSize(1);
        assertThat(contexto.getBeansOfType(CuentaEscrituraInterna.class)).hasSize(1);
        assertThat(contexto.getBeansOfType(ConsultaCuentasService.class)).isEmpty();
        // EP-01-T003/T004 publican la consulta (GET del listado y del detalle) y FEAT-02 (SPEC-04) solo
        // la baja lógica (DELETE del detalle); ninguna otra escritura sobre estas rutas.
        var rutasUsuarios = rutas.getHandlerMethods().keySet().stream()
                .filter(r -> r.getPatternValues().contains("/api/v1/usuarios")
                        || r.getPatternValues().contains("/api/v1/usuarios/{id}"))
                .toList();
        assertThat(rutasUsuarios).hasSize(3);
        assertThat(rutasUsuarios).filteredOn(r -> r.getMethodsCondition().getMethods().contains(RequestMethod.GET))
                .hasSize(2);
        assertThat(rutasUsuarios).filteredOn(r -> r.getMethodsCondition().getMethods().contains(RequestMethod.DELETE))
                .singleElement().satisfies(r -> assertThat(r.getPatternValues()).containsExactly("/api/v1/usuarios/{id}"));
    }
}
