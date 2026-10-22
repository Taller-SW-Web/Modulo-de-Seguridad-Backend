package com.marketplace.auth_service.configuration;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.usuario.adapter.in.transaction.ConsultaUsuariosTransaccional;
import com.marketplace.auth_service.usuario.adapter.in.transaction.CuentasPortTransaccional;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.*;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.*;
import com.marketplace.auth_service.usuario.application.port.in.*;
import com.marketplace.auth_service.usuario.application.port.out.*;
import com.marketplace.auth_service.usuario.application.service.ConsultaCuentasService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class UsuarioConfigurationTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @ComponentScan(basePackages="com.marketplace.auth_service.usuario",
            // Solo componentes de producción: fuera las configuraciones propias de otras pruebas.
            excludeFilters=@ComponentScan.Filter(type=FilterType.ANNOTATION,
                    classes={org.springframework.boot.test.context.TestConfiguration.class,
                            org.springframework.boot.SpringBootConfiguration.class}))
    static class EscaneoReal { }
    final ApplicationContextRunner runner=new ApplicationContextRunner()
            .withUserConfiguration(UsuarioConfiguration.class,EscaneoReal.class)
            .withBean(UsuarioJpaRepository.class,()->mock(UsuarioJpaRepository.class))
            .withBean(UsuarioEstadoEfectivoRepository.class,()->mock(UsuarioEstadoEfectivoRepository.class))
            .withBean(AutorizacionPort.class,()->mock(AutorizacionPort.class))
            // El registro se cablea en RegistroClienteConfig; aquí solo lo necesita su controlador escaneado.
            .withBean(RegistroClientePort.class,()->mock(RegistroClientePort.class));
    @Test void unSoloBeanPorPuertoYUnicoCuentasPortDecoradoConEscaneoReal() {
        runner.run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(CuentasPort.class)
                    .hasSingleBean(CuentaEscrituraInterna.class).hasSingleBean(UsuarioRepositoryPort.class)
                    .hasSingleBean(RolesConsultaPort.class);
            assertThat(c.getBean(CuentasPort.class)).isInstanceOf(CuentasPortTransaccional.class);
            assertThat(c.getBeansOfType(ConsultaCuentasService.class)).isEmpty();
            assertThat(c.getBeansOfType(UsuarioRepositoryAdapter.class)).hasSize(1);
            assertThat(c.getBeansOfType(CuentaEscrituraAdapter.class)).hasSize(1);
            assertThat(c).hasSingleBean(ConsultaUsuariosPort.class);
            assertThat(c.getBean(ConsultaUsuariosPort.class)).isInstanceOf(ConsultaUsuariosTransaccional.class);
        });
    }
}
