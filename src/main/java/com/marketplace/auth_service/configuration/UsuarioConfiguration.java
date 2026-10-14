package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.UsuarioRepositoryAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioEstadoEfectivoRepository;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioEstadoEfectivoPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import com.marketplace.auth_service.usuario.application.service.ConsultaCuentasService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UsuarioConfiguration {

    @Bean
    public UsuarioRepositoryAdapter usuarioRepositoryAdapter(UsuarioJpaRepository jpaRepository,
                                                               UsuarioEstadoEfectivoRepository estadoEfectivoRepository) {
        return new UsuarioRepositoryAdapter(jpaRepository, estadoEfectivoRepository);
    }

    @Bean
    public UsuarioRepositoryPort usuarioRepositoryPort(UsuarioRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    public UsuarioEstadoEfectivoPort usuarioEstadoEfectivoPort(UsuarioRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    public CuentasPort cuentasPort(UsuarioRepositoryPort usuarioRepositoryPort,
                                    UsuarioEstadoEfectivoPort usuarioEstadoEfectivoPort) {
        return new ConsultaCuentasService(usuarioRepositoryPort, usuarioEstadoEfectivoPort);
    }
}