package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.proteccion.application.port.out.HuellaProteccionPort;
import com.marketplace.auth_service.proteccion.application.service.HuellaProteccionDenegada;
import com.marketplace.auth_service.proteccion.domain.exception.HuellaNoDisponibleException;
import com.marketplace.auth_service.proteccion.domain.model.RachaFallo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ProteccionCriptografiaConfigTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(ProteccionCriptografiaConfig.class);
    @Test void sinConfiguracionArrancaPeroDeniegaGeneracion() {
        runner.run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(HuellaProteccionPort.class);
            assertThat(c.getBean(HuellaProteccionPort.class)).isInstanceOf(HuellaProteccionDenegada.class);
            assertThatThrownBy(() -> c.getBean(HuellaProteccionPort.class).claveActualId()).isInstanceOf(HuellaNoDisponibleException.class);
        });
    }
    @Test void clavePersistenteConservaTagEnDosArranques() {
        String key=Base64.getEncoder().encodeToString(new byte[32]);
        String[] tags=new String[2];
        var r=new RachaFallo(UUID.randomUUID(),UUID.randomUUID(),"test",Instant.EPOCH);
        for(int i=0;i<2;i++) {
            int n=i;
            runner.withPropertyValues("AUTH_FALLOS_HMAC_KEY_ID=test","AUTH_FALLOS_HMAC_KEY="+key).run(c ->
                tags[n]=c.getBean(HuellaProteccionPort.class).generar(r,new byte[]{1,2,3},Instant.EPOCH).huellaHmac());
        }
        assertThat(tags[0]).isEqualTo(tags[1]);
    }
    @Test void base64InvalidaDeniega() {
        runner.withPropertyValues("AUTH_FALLOS_HMAC_KEY_ID=test","AUTH_FALLOS_HMAC_KEY=material-invalido").run(c ->
                assertThat(c.getBean(HuellaProteccionPort.class)).isInstanceOf(HuellaProteccionDenegada.class));
    }
    @Test void claveCortaDeniegaSinClaveTemporal() {
        runner.withPropertyValues("AUTH_FALLOS_HMAC_KEY_ID=test","AUTH_FALLOS_HMAC_KEY="+Base64.getEncoder().encodeToString(new byte[31])).run(c ->
                assertThat(c.getBean(HuellaProteccionPort.class)).isInstanceOf(HuellaProteccionDenegada.class));
    }
    @Test void identificadorInvalidoDeniega() {
        runner.withPropertyValues("AUTH_FALLOS_HMAC_KEY_ID="+"k".repeat(51),"AUTH_FALLOS_HMAC_KEY="+Base64.getEncoder().encodeToString(new byte[32])).run(c ->
                assertThat(c.getBean(HuellaProteccionPort.class)).isInstanceOf(HuellaProteccionDenegada.class));
    }
}
