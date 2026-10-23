package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RolClienteInicialJpaAdapter;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.port.in.RolClienteInicialInterna;
import com.marketplace.auth_service.credencial.adapter.out.persistence.CredencialInicialJpaAdapter;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.CredencialJpaRepository;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.PasswordHistorialJpaRepository;
import com.marketplace.auth_service.credencial.application.port.in.CredencialInicialInterna;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.CorreoVerificacionAdapter;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.EfectosRegistroAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.AltaCuentaJpaAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.port.in.RegistroClientePort;
import com.marketplace.auth_service.usuario.application.port.out.AltaCuentaPort;
import com.marketplace.auth_service.usuario.application.port.out.CorreoVerificacionPort;
import com.marketplace.auth_service.usuario.application.service.RegistroClienteService;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wiring del registro público (SPEC-01, FEAT-01-T1), aparte de UsuarioConfiguration para no
 * cargarlo en sus pruebas. Las altas de credencial y rol son de sus dueños y solo se consumen
 * por sus puertos exportados.
 */
@Configuration(proxyBeanMethods = false)
public class RegistroClienteConfig {

    @Bean
    AltaCuentaPort altaCuentaPort(UsuarioJpaRepository usuarios) {
        return new AltaCuentaJpaAdapter(usuarios);
    }

    @Bean
    CredencialInicialInterna credencialInicialInterna(CredencialJpaRepository credenciales,
                                                      PasswordHistorialJpaRepository historiales,
                                                      PasswordHasherPort hasher, ObjectProvider<Clock> clock) {
        return new CredencialInicialJpaAdapter(credenciales, historiales, hasher, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    RolClienteInicialInterna rolClienteInicialInterna(UsuarioRolJpaRepository usuarioRol) {
        return new RolClienteInicialJpaAdapter(usuarioRol);
    }

    /** El enlace usa el mismo origen público de la SPA que el desbloqueo ({@code G7_SPA_URL}). */
    @Bean
    CorreoVerificacionPort correoVerificacionPort(TokensUnUsoPort tokens, MensajeriaPort mensajeria,
                                                  @Value("${g7.spa.url:http://localhost:5173}") String urlSpa) {
        return new CorreoVerificacionAdapter(tokens, mensajeria, urlSpa);
    }

    @Bean
    RegistroClientePort registroClientePort(AltaCuentaPort altas, CredencialesPort politica,
                                            CredencialInicialInterna credenciales, RolClienteInicialInterna roles,
                                            AuditoriaPort auditoria, CorreoVerificacionPort correoVerificacion,
                                            PlatformTransactionManager transacciones, ObjectProvider<Clock> reloj,
                                            @Value("${g7.registro.version-terminos}") String versionTerminos) {
        Clock clock = reloj.getIfAvailable(Clock::systemUTC);
        return new RegistroClienteService(altas,
                new EfectosRegistroAdapter(politica, credenciales, roles, auditoria, transacciones, clock),
                correoVerificacion, versionTerminos, clock);
    }
}
