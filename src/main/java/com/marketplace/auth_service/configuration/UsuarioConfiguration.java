package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.identidad.application.port.in.ConsultaIdentidadPort;
import com.marketplace.auth_service.proteccion.application.port.in.ConsultaBloqueosPort;
import com.marketplace.auth_service.usuario.adapter.in.transaction.ConsultaUsuariosTransaccional;
import com.marketplace.auth_service.usuario.adapter.in.transaction.CuentasPortTransaccional;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.AutorizacionUsuariosAdapter;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.BloqueosConsultaAdapter;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.ConsultaIdentidadServicioAdapter;
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
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * Listado y detalle administrativos (EP-01-T003/T004) sobre CuentasPort y AutorizacionPort,
     * con el bloqueo vigente leído de protección (RF-15.6).
     */
    @Bean
    ConsultaUsuariosPort consultaUsuariosPort(CuentasPort cuentas, AutorizacionPort autorizacion,
                                              ObjectProvider<ConsultaBloqueosPort> bloqueos,
                                              ObjectProvider<ConsultaIdentidadPort> identidad) {
        // Se resuelve al consultar: los contextos parciales que no componen protección
        // arrancan igual, y si se lee un BLOQUEADO sin proveedor falla explícito (nunca omite).
        ConsultaBloqueosPort proteccion = ids -> bloqueos.getObject().vigentes(ids);
        AutorizacionUsuariosAdapter autorizacionAdapter = new AutorizacionUsuariosAdapter(autorizacion);
        BloqueosConsultaAdapter bloqueosAdapter = new BloqueosConsultaAdapter(proteccion);
        // Igual que arriba: un contexto parcial sin IntegracionConfig (SPEC-18) sigue
        // componiendo EP-01-T003/T004; obtenerParaServicio solo falla si de verdad se invoca.
        ConsultaIdentidadPort identidadPort = identidad.getIfAvailable();
        ConsultaUsuariosService servicio = identidadPort == null
                ? new ConsultaUsuariosService(cuentas, autorizacionAdapter, bloqueosAdapter)
                : new ConsultaUsuariosService(cuentas, autorizacionAdapter, bloqueosAdapter,
                        new ConsultaIdentidadServicioAdapter(identidadPort));
        return new ConsultaUsuariosTransaccional(servicio);
    }
}
