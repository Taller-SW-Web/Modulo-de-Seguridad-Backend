package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.RolesPruebaJpaAdapter;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.port.in.RolesPruebaInterna;
import com.marketplace.auth_service.credencial.adapter.out.persistence.CredencialPruebaJpaAdapter;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.CredencialJpaRepository;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.PasswordHistorialJpaRepository;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CredencialPruebaInterna;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.AltaCuentaPruebaAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.port.in.AltaCuentaPruebaInterna;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Cuentas de prueba TEC-02 (EP-01-T007). Desactivado por defecto: sin
 * {@code g7.fixtures.cuentas.habilitadas=true} no existe ninguno de estos beans, así
 * que producción no tiene alta técnica de cuentas, credenciales ni roles.
 *
 * <p>La contraseña común de las cuentas llega por {@code g7.fixtures.cuentas.clave}
 * (variable de entorno {@code G7_FIXTURES_CUENTAS_CLAVE}), nunca desde Git, y debe
 * cumplir la política real. La carga corre al arrancar y registra solo etiqueta,
 * UUID, correo, roles y si se creó.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "g7.fixtures.cuentas", name = "habilitadas", havingValue = "true")
public class CuentasPruebaConfig {

    private static final Logger log = LoggerFactory.getLogger(CuentasPruebaConfig.class);

    @Bean
    AltaCuentaPruebaInterna altaCuentaPruebaInterna(UsuarioJpaRepository usuarios, ObjectProvider<Clock> clock) {
        return new AltaCuentaPruebaAdapter(usuarios, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    CredencialPruebaInterna credencialPruebaInterna(CredencialJpaRepository credenciales,
                                                    PasswordHistorialJpaRepository historiales,
                                                    PasswordHasherPort hasher, ObjectProvider<Clock> clock) {
        return new CredencialPruebaJpaAdapter(credenciales, historiales, hasher, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    RolesPruebaInterna rolesPruebaInterna(UsuarioRolJpaRepository usuarioRol) {
        return new RolesPruebaJpaAdapter(usuarioRol);
    }

    @Bean
    CargaCuentasPrueba cargaCuentasPrueba(AltaCuentaPruebaInterna cuentas, CredencialPruebaInterna credenciales,
                                          RolesPruebaInterna roles, CredencialesPort politica,
                                          PlatformTransactionManager transacciones,
                                          @Value("${g7.fixtures.cuentas.clave:}") String clave) {
        if (clave.isBlank()) {
            throw new IllegalStateException("g7.fixtures.cuentas.habilitadas=true exige g7.fixtures.cuentas.clave "
                    + "(G7_FIXTURES_CUENTAS_CLAVE)");
        }
        return new CargaCuentasPrueba(cuentas, credenciales, roles, politica, transacciones, new SecretoEfimero(clave));
    }

    @Bean
    ApplicationRunner cargarCuentasPruebaAlArrancar(CargaCuentasPrueba carga) {
        return argumentos -> {
            log.warn("Carga de cuentas de prueba habilitada (solo entornos de prueba)");
            for (var r : carga.cargar()) {
                log.info("cuenta de prueba {} id={} correo={} roles={} {}", r.etiqueta(), r.id(), r.correo(), r.roles(),
                        r.creada() ? "creada" : "ya existía, sin cambios");
            }
        };
    }
}
