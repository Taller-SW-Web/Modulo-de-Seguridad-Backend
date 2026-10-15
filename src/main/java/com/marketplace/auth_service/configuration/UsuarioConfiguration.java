package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.usuario.adapter.in.transaction.ConsultaUsuariosTransaccional;
import com.marketplace.auth_service.usuario.adapter.in.transaction.CuentasPortTransaccional;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.AutorizacionUsuariosAdapter;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.RolesConsultaAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.CuentaEscrituraAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.UsuarioRepositoryAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioEstadoEfectivoRepository;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.port.in.ConsultaUsuariosPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.port.out.RolesConsultaPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import com.marketplace.auth_service.usuario.application.service.ConsultaCuentasService;
import com.marketplace.auth_service.usuario.application.service.ConsultaUsuariosService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UsuarioConfiguration {
    @Bean
    UsuarioRepositoryAdapter usuarioRepositoryAdapter(UsuarioJpaRepository usuarios, UsuarioEstadoEfectivoRepository estados) {
        return new UsuarioRepositoryAdapter(usuarios, estados);
    }
    @Bean
    CuentaEscrituraAdapter cuentaEscrituraAdapter(UsuarioJpaRepository usuarios, UsuarioRepositoryPort consultas) {
        return new CuentaEscrituraAdapter(usuarios, consultas);
    }
    @Bean
    RolesConsultaAdapter rolesConsultaAdapter(AutorizacionPort autorizacion) {
        return new RolesConsultaAdapter(autorizacion);
    }
    @Bean
    CuentasPort cuentasPort(UsuarioRepositoryPort usuarios, RolesConsultaPort roles) {
        return new CuentasPortTransaccional(new ConsultaCuentasService(usuarios, roles));
    }

    /** Listado y detalle administrativos (EP-01-T003/T004) sobre CuentasPort y AutorizacionPort. */
    @Bean
    ConsultaUsuariosPort consultaUsuariosPort(CuentasPort cuentas, AutorizacionPort autorizacion) {
        return new ConsultaUsuariosTransaccional(
                new ConsultaUsuariosService(cuentas, new AutorizacionUsuariosAdapter(autorizacion)));
    }
}
