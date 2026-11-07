package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.identidad.adapter.in.transaction.ConsultaIdentidadTransaccional;
import com.marketplace.auth_service.identidad.adapter.in.transaction.TokenServicioTransaccional;
import com.marketplace.auth_service.identidad.adapter.out.capacidad.AuditoriaAccesoAdapter;
import com.marketplace.auth_service.identidad.adapter.out.capacidad.AuditoriaIdentidadAdapter;
import com.marketplace.auth_service.identidad.adapter.out.capacidad.CuentasIdentidadAdapter;
import com.marketplace.auth_service.identidad.adapter.out.capacidad.DocumentoIdentidadAdapter;
import com.marketplace.auth_service.identidad.adapter.out.capacidad.EstadoIdentidadAdapter;
import com.marketplace.auth_service.identidad.adapter.out.capacidad.RolesIdentidadAdapter;
import com.marketplace.auth_service.identidad.adapter.out.persistence.ClientesServicioJdbcAdapter;
import com.marketplace.auth_service.identidad.application.port.in.ConsultaIdentidadPort;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.port.in.IntrospeccionPort;
import com.marketplace.auth_service.identidad.application.port.in.TokenServicioPort;
import com.marketplace.auth_service.identidad.application.port.out.AuditoriaAccesoPort;
import com.marketplace.auth_service.identidad.application.service.ConsultaIdentidadService;
import com.marketplace.auth_service.identidad.application.service.EmitirTokenServicioService;
import com.marketplace.auth_service.identidad.application.service.IntrospeccionService;
import com.marketplace.auth_service.perfil.application.port.in.ConsultaDocumentoExportPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wiring de la integración con módulos consumidores (SPEC-17, FEAT-05).
 *
 * <p>La introspección no abre una transacción envolvente: cada consulta abre
 * la suya. Sesiones exige una existente (MANDATORY), así que
 * {@link EstadoIdentidadAdapter} la abre con el gestor; cuentas y autorización
 * traen la propia. Un acceso revocado llega de sesiones como rechazo que marca
 * su transacción para revertir; si compartiera una transacción común, el
 * rechazo esperado la invalidaría entera.
 * Los proveedores se resuelven al usarse, para que los contextos parciales de
 * otras pruebas arranquen sin ellos.
 */
@Configuration(proxyBeanMethods = false)
public class IntegracionConfig {

    @Bean
    TokenServicioPort tokenServicioPort(JdbcTemplate jdbc, FirmaPort firma, PlatformTransactionManager gestor) {
        return new TokenServicioTransaccional(
                new EmitirTokenServicioService(new ClientesServicioJdbcAdapter(jdbc), firma), gestor);
    }

    @Bean
    IntrospeccionPort introspeccionPort(FirmaPort firma, ObjectProvider<SesionesPort> sesiones,
                                        ObjectProvider<CuentasPort> cuentas,
                                        ObjectProvider<AutorizacionPort> autorizacion,
                                        PlatformTransactionManager gestor) {
        return (solicitante, token) -> new IntrospeccionService(firma, new EstadoIdentidadAdapter(
                sesiones.getObject(), cuentas.getObject(), autorizacion.getObject(), gestor))
                .introspeccionar(solicitante, token);
    }

    /** 403 de scope como {@code ACCESO_DENEGADO} (SPEC-17 ESC-17.8). */
    @Bean
    AuditoriaAccesoPort auditoriaAccesoPort(AuditoriaPort auditoria) {
        return new AuditoriaAccesoAdapter(auditoria);
    }

    /** SPEC-18 FEAT-06-T1: {@code GET /usuarios/{id}} compartido con el token de servicio. */
    @Bean
    ConsultaIdentidadPort consultaIdentidadPort(CuentasPort cuentas, AutorizacionPort autorizacion,
                                                ConsultaDocumentoExportPort documentos, AuditoriaPort auditoria,
                                                ObjectProvider<Clock> reloj) {
        return new ConsultaIdentidadTransaccional(new ConsultaIdentidadService(
                new CuentasIdentidadAdapter(cuentas), new RolesIdentidadAdapter(autorizacion),
                new DocumentoIdentidadAdapter(documentos), new AuditoriaIdentidadAdapter(auditoria),
                reloj.getIfAvailable(Clock::systemUTC)));
    }
}
