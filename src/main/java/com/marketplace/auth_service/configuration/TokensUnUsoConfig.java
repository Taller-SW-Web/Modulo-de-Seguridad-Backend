package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.credencial.adapter.in.transaction.TokensUnUsoTransaccional;
import com.marketplace.auth_service.credencial.adapter.out.persistence.TokenUnUsoJpaAdapter;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.TokenUnUsoJpaRepository;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.credencial.application.port.out.TokenUnUsoRepositoryPort;
import com.marketplace.auth_service.credencial.application.service.TokensUnUsoService;
import java.security.SecureRandom;
import java.time.Clock;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TokensUnUsoConfig {
    @Bean TokenUnUsoRepositoryPort tokenUnUsoRepositoryPort(TokenUnUsoJpaRepository repository, EntityManager entityManager) {
        return new TokenUnUsoJpaAdapter(repository, entityManager);
    }
    @Bean TokensUnUsoPort tokensUnUsoPort(TokenUnUsoRepositoryPort repository, ObjectProvider<Clock> clocks,
                                           ObjectProvider<SecureRandom> randoms) {
        TokensUnUsoService service = new TokensUnUsoService(repository, clocks.getIfAvailable(Clock::systemUTC),
                randoms.getIfAvailable(SecureRandom::new));
        return new TokensUnUsoTransaccional(service);
    }
}
