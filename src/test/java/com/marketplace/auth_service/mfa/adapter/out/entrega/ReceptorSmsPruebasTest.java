package com.marketplace.auth_service.mfa.adapter.out.entrega;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.*;

class ReceptorSmsPruebasTest {
    @Test void receptorNoExisteSinPerfilExplicito() {
        try(var contexto=new AnnotationConfigApplicationContext()) {
            contexto.register(ReceptorSmsPruebas.class); contexto.refresh();
            assertThat(contexto.getBeansOfType(ReceptorSmsPruebas.class)).isEmpty();
        }
    }
    @Test void perfilDePruebasRetieneEntregaSoloEnReceptorAcotadoYRedactado() {
        try(var contexto=new AnnotationConfigApplicationContext()) {
            contexto.getEnvironment().setActiveProfiles("pruebas-sms");
            contexto.register(ReceptorSmsPruebas.class); contexto.refresh();
            var receptor=contexto.getBean(ReceptorSmsPruebas.class);
            for(int i=0;i<51;i++) receptor.recibir("999123456","012345");
            var entregas=receptor.retirar();
            assertThat(entregas).hasSize(50); assertThat(entregas.getFirst().codigo()).isEqualTo("012345");
            assertThat(entregas.toString()).doesNotContain("012345","999123456"); assertThat(receptor.retirar()).isEmpty();
        }
    }
}
