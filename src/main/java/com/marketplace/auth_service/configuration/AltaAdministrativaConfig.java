package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RolInicialPersonalJpaAdapter;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.in.RolInicialPersonalInterna;
import com.marketplace.auth_service.credencial.application.port.in.CredencialInicialInterna;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.CorreoActivacionAdapter;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.EfectosActivacionAdapter;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.EfectosAltaPersonalAdapter;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.TokensActivacionAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.CuentasPersonalJpaAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.port.in.ActivacionCuentaPort;
import com.marketplace.auth_service.usuario.application.port.in.AltaPersonalPort;
import com.marketplace.auth_service.usuario.application.port.out.CorreoActivacionPort;
import com.marketplace.auth_service.usuario.application.port.out.CuentasPersonalPort;
import com.marketplace.auth_service.usuario.application.service.ActivacionCuentaService;
import com.marketplace.auth_service.usuario.application.service.AltaPersonalService;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Alta administrativa de personal y su activación por enlace (SPEC-03 RF-03.1 y RF-03.5, FEAT-13-T1).
 * Reutiliza la política, la primera credencial y el outbox del registro público (FEAT-01).
 */
@Configuration(proxyBeanMethods = false)
public class AltaAdministrativaConfig {

    @Bean
    CuentasPersonalPort cuentasPersonalPort(UsuarioJpaRepository usuarios) {
        return new CuentasPersonalJpaAdapter(usuarios);
    }

    @Bean
    RolInicialPersonalInterna rolInicialPersonalInterna(UsuarioRolJpaRepository usuarioRol) {
        return new RolInicialPersonalJpaAdapter(usuarioRol);
    }

    /** El enlace usa el mismo origen público de la SPA que la verificación ({@code G7_SPA_URL}). */
    @Bean
    CorreoActivacionPort correoActivacionPort(TokensUnUsoPort tokens, MensajeriaPort mensajeria,
                                              @Value("${g7.spa.url:http://localhost:5173}") String urlSpa) {
        return new CorreoActivacionAdapter(tokens, mensajeria, urlSpa);
    }

    @Bean
    AltaPersonalPort altaPersonalPort(CuentasPersonalPort cuentas, AutorizacionPort autorizacion,
                                      RolInicialPersonalInterna roles, AuditoriaPort auditoria,
                                      CorreoActivacionPort correoActivacion, PlatformTransactionManager transacciones,
                                      ObjectProvider<Clock> reloj) {
        return new AltaPersonalService(cuentas,
                new EfectosAltaPersonalAdapter(autorizacion, roles, auditoria, transacciones),
                correoActivacion, reloj.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    ActivacionCuentaPort activacionCuentaPort(TokensUnUsoPort tokens, CuentasPersonalPort cuentas,
                                              CredencialesPort politica, CredencialInicialInterna credenciales,
                                              AutorizacionPort autorizacion, MensajeriaPort mensajeria,
                                              AuditoriaPort auditoria, PlatformTransactionManager transacciones,
                                              ObjectProvider<Clock> reloj,
                                              @Value("${g7.registro.version-terminos}") String versionTerminos) {
        return new ActivacionCuentaService(new TokensActivacionAdapter(tokens), cuentas,
                new EfectosActivacionAdapter(politica, credenciales, autorizacion, mensajeria, auditoria, transacciones),
                versionTerminos, reloj.getIfAvailable(Clock::systemUTC));
    }
}
