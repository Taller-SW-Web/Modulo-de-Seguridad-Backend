package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.sesion.adapter.in.transaction.LoginTransaccional;
import com.marketplace.auth_service.sesion.adapter.in.transaction.SesionTransaccional;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.CredencialesLoginPendiente;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.CuentasLoginPendiente;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.IdentidadSesionPendiente;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.MfaLoginPendiente;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.ProteccionLoginPendiente;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteAutorizacionSesion;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteFirmaAcceso;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteFirmaAcceso;
import com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter;
import com.marketplace.auth_service.sesion.adapter.out.persistence.SesionJpaAdapter;
import com.marketplace.auth_service.sesion.adapter.out.persistence.repository.AccesoSesionJpaRepository;
import com.marketplace.auth_service.sesion.adapter.out.persistence.repository.SesionFamiliaJpaRepository;
import com.marketplace.auth_service.sesion.adapter.out.persistence.repository.TokenRefrescoJpaRepository;
import com.marketplace.auth_service.sesion.application.port.in.LoginPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.AutorizacionSesionPort;
import com.marketplace.auth_service.sesion.application.port.out.CredencialesLoginPort;
import com.marketplace.auth_service.sesion.application.port.out.CriptografiaSesionPort;
import com.marketplace.auth_service.sesion.application.port.out.CuentasLoginPort;
import com.marketplace.auth_service.sesion.application.port.out.FirmadorAccesoPort;
import com.marketplace.auth_service.sesion.application.port.out.MfaLoginPort;
import com.marketplace.auth_service.sesion.application.port.out.PersistenciaSesionPort;
import com.marketplace.auth_service.sesion.application.port.out.ProteccionLoginPort;
import com.marketplace.auth_service.sesion.application.port.out.ProveedorIdentidadPort;
import com.marketplace.auth_service.sesion.application.service.LoginService;
import com.marketplace.auth_service.sesion.application.service.SesionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wiring de la capacidad de sesión (EP-02-T003). El caso de uso puro se crea
 * aquí dentro de su decorador transaccional, así {@link SesionesPort} tiene un
 * único bean y nadie lo llama sin frontera transaccional. La firma llega por
 * el puente al {@code FirmaPort} de Sergio y los roles por el puente al
 * {@code AutorizacionPort}; la cuenta, credencial, protección y MFA usan
 * marcadores pendientes de Eva, Juan José y Luis (503 en voz alta) hasta la
 * composición real (T007).
 */
@Configuration(proxyBeanMethods = false)
public class SesionConfig {

    @Bean
    PersistenciaSesionPort persistenciaSesionPort(SesionFamiliaJpaRepository familias,
                                                  TokenRefrescoJpaRepository tokens,
                                                  AccesoSesionJpaRepository accesos) {
        return new SesionJpaAdapter(familias, tokens, accesos);
    }

    @Bean
    CriptografiaSesionPort criptografiaSesionPort() {
        return new SesionCryptoAdapter();
    }

    @Bean
    FirmadorAccesoPort firmadorAccesoPort(FirmaPort firma) {
        return new PuenteFirmaAcceso(firma);
    }

    @Bean
    ProveedorIdentidadPort proveedorIdentidadPort() {
        return new IdentidadSesionPendiente();
    }

    @Bean
    SesionesPort sesionesPort(PersistenciaSesionPort persistencia, CriptografiaSesionPort criptografia,
                              FirmadorAccesoPort firmador, ProveedorIdentidadPort identidad,
                              ObjectProvider<Clock> clock) {
        return new SesionTransaccional(
                new SesionService(persistencia, criptografia, firmador, identidad,
                        clock.getIfAvailable(Clock::systemUTC)));
    }

    @Bean
    CuentasLoginPort cuentasLoginPort() {
        return new CuentasLoginPendiente();
    }

    @Bean
    CredencialesLoginPort credencialesLoginPort() {
        return new CredencialesLoginPendiente();
    }

    @Bean
    ProteccionLoginPort proteccionLoginPort() {
        return new ProteccionLoginPendiente();
    }

    @Bean
    MfaLoginPort mfaLoginPort() {
        return new MfaLoginPendiente();
    }

    @Bean
    AutorizacionSesionPort autorizacionSesionPort(AutorizacionPort autorizacion) {
        return new PuenteAutorizacionSesion(autorizacion);
    }

    @Bean
    LoginPort loginPort(CuentasLoginPort cuentas, CredencialesLoginPort credenciales,
                        ProteccionLoginPort proteccion, MfaLoginPort mfa,
                        AutorizacionSesionPort autorizacion, SesionesPort sesiones,
                        ObjectProvider<Clock> clock) {
        return new LoginTransaccional(new LoginService(cuentas, credenciales, proteccion, mfa,
                autorizacion, sesiones, clock.getIfAvailable(Clock::systemUTC)));
    }
}
