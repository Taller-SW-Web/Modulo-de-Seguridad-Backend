package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.usuario.adapter.in.transaction.CuentasPortTransaccional;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.CuentaEscrituraAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.UsuarioRepositoryAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioEstadoEfectivoRepository;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentaEscrituraInterna;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioEstadoEfectivoPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import com.marketplace.auth_service.usuario.application.service.ConsultaCuentasService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class UsuarioConfiguration {

    @Bean
    @Primary
    public UsuarioRepositoryPort usuarioRepositoryPort(UsuarioJpaRepository jpaRepository,
                                                        UsuarioEstadoEfectivoRepository estadoEfectivoRepository) {
        return new UsuarioRepositoryAdapter(jpaRepository, estadoEfectivoRepository);
    }

    @Bean
    public UsuarioEstadoEfectivoPort usuarioEstadoEfectivoPort(UsuarioRepositoryPort usuarioRepositoryPort) {
        return (UsuarioEstadoEfectivoPort) usuarioRepositoryPort;
    }

    @Bean
    public ConsultaCuentasService consultaCuentasService(UsuarioRepositoryPort usuarioRepositoryPort,
                                                          UsuarioEstadoEfectivoPort usuarioEstadoEfectivoPort) {
        return new ConsultaCuentasService(usuarioRepositoryPort, usuarioEstadoEfectivoPort);
    }

    @Bean
    public CuentaEscrituraAdapter cuentaEscrituraAdapter(UsuarioJpaRepository jpaRepository) {
        return new CuentaEscrituraAdapter(jpaRepository);
    }

    @Bean
    public CuentaEscrituraInterna cuentaEscrituraInterna(CuentaEscrituraAdapter adapter) {
        return adapter;
    }

    @Bean
    @Primary
    public CuentasPort cuentasPort(ConsultaCuentasService consultaService,
                                    CuentaEscrituraAdapter escrituraAdapter,
                                    UsuarioRepositoryPort usuarioRepositoryPort) {
        return new CuentasPortTransaccional(consultaService, escrituraAdapter, usuarioRepositoryPort);
    }
}