package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.sesion.adapter.in.transaction.SesionTransaccional;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteFirmaAcceso;
import com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter;
import com.marketplace.auth_service.sesion.adapter.out.persistence.SesionJpaAdapter;
import com.marketplace.auth_service.sesion.adapter.out.persistence.repository.AccesoSesionJpaRepository;
import com.marketplace.auth_service.sesion.adapter.out.persistence.repository.SesionFamiliaJpaRepository;
import com.marketplace.auth_service.sesion.adapter.out.persistence.repository.TokenRefrescoJpaRepository;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.CriptografiaSesionPort;
import com.marketplace.auth_service.sesion.application.port.out.FirmadorAccesoPort;
import com.marketplace.auth_service.sesion.application.port.out.PersistenciaSesionPort;
import com.marketplace.auth_service.sesion.application.service.SesionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wiring de la capacidad de sesión (EP-02-T003). El caso de uso puro se crea
 * aquí dentro de su decorador transaccional, así {@link SesionesPort} tiene un
 * único bean y nadie lo llama sin frontera transaccional. La firma llega por
 * el puente al {@code FirmaPort} de Sergio; la cuenta, por el coordinador que
 * ya la resolvió con Eva (su {@code CuentasPort} aún no existe en el repo).
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
    SesionesPort sesionesPort(PersistenciaSesionPort persistencia, CriptografiaSesionPort criptografia,
                              FirmadorAccesoPort firmador, ObjectProvider<Clock> clock) {
        return new SesionTransaccional(
                new SesionService(persistencia, criptografia, firmador, clock.getIfAvailable(Clock::systemUTC)));
    }
}
