package com.marketplace.auth_service.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.marketplace.auth_service.credencial.adapter.in.transaction.TokensUnUsoTransaccional;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.TokenUnUsoJpaRepository;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class TokensUnUsoConfigTest {

    @Test
    void exponeUnUnicoPortYEsElDecoradorTransaccional() {
        try (var contexto = new AnnotationConfigApplicationContext()) {
            contexto.registerBean(TokenUnUsoJpaRepository.class, () -> mock(TokenUnUsoJpaRepository.class));
            contexto.registerBean(EntityManager.class, () -> mock(EntityManager.class));
            contexto.register(TokensUnUsoConfig.class);
            contexto.refresh();

            assertThat(contexto.getBeansOfType(TokensUnUsoPort.class))
                    .containsOnlyKeys("tokensUnUsoPort");
            assertThat(contexto.getBean(TokensUnUsoPort.class))
                    .isInstanceOf(TokensUnUsoTransaccional.class);
        }
    }
}
