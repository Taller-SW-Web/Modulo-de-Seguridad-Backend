package com.marketplace.auth_service;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
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
        assertThat(rutas.getHandlerMethods().keySet().stream().flatMap(r -> r.getPatternValues().stream()))
                .doesNotContain("/api/v1/usuarios", "/api/v1/usuarios/{id}");
    }
}
