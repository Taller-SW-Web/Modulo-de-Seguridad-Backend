package com.marketplace.auth_service.proteccion.adapter.in.web;
import com.fasterxml.jackson.databind.*;
import com.marketplace.auth_service.configuration.*;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.proteccion.application.dto.*;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import com.marketplace.auth_service.proteccion.domain.exception.ProteccionNoDisponibleException;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

/** Solo contrato HTTP/filtro real. El puerto controlado NO acredita integración con tokens/efectos. */
@SpringBootTest(classes=DesbloqueoHttpTest.App.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class DesbloqueoHttpTest {
    static final AtomicReference<Function<PruebaEnlace,ResultadoDesbloqueo>> RESPUESTA=new AtomicReference<>();
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class,SeguridadConfig.class,DesbloqueoController.class,ProteccionErroresAdvice.class})
    static class App {
        @Bean ProteccionPort puertoControlado() {
            return new ProteccionPort() {
                public EvaluacionProteccion evaluar(CuentaSnapshot c,Instant a){throw new UnsupportedOperationException();}
                public ResultadoFallo registrarFallo(CuentaSnapshot c,HuellaFallo h,ContextoPeticion p){throw new UnsupportedOperationException();}
                public void completarLogin(UUID u){throw new UnsupportedOperationException();}
                public ResultadoDesbloqueo desbloquear(PruebaEnlace p,ContextoPeticion c){return RESPUESTA.get().apply(p);}
                public void prepararRestablecimiento(UUID u){throw new UnsupportedOperationException();}
                public void restablecerPorRecuperacion(UUID u,ContextoPeticion c,Instant a){throw new UnsupportedOperationException();}
            };
        }
    }
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("identidad.issuer",()->"https://proteccion.example.test/api/v1/auth");
        r.add("identidad.clave-privada",()->ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }
    @LocalServerPort int puerto;
    final HttpClient http=HttpClient.newHttpClient();
    final ObjectMapper json=new ObjectMapper();
    @BeforeEach void preparar(){RESPUESTA.set(p->ResultadoDesbloqueo.DESBLOQUEADA);}
    HttpResponse<String> enviar(String metodo,String body,String bearer) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+puerto+"/api/v1/auth/desbloquear"))
                .header("Content-Type","application/json").method(metodo,HttpRequest.BodyPublishers.ofString(body));
        if(bearer!=null)req.header("Authorization","Bearer "+bearer);
        return http.send(req.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void postPublicoDevuelve204SinBearerYConAccesoVencido() throws Exception {
        assertThat(enviar("POST","{\"token\":\"sintetico\"}",null).statusCode()).isEqualTo(204);
        assertThat(enviar("POST","{\"token\":\"sintetico\"}","vencido").statusCode()).isEqualTo(204);
    }
    @Test void codigosCanonicosInvalidoYExpirado() throws Exception {
        RESPUESTA.set(p->ResultadoDesbloqueo.INVALIDA);
        var invalido=enviar("POST","{\"token\":\"sintetico\"}",null);
        assertThat(invalido.statusCode()).isEqualTo(401);
        assertThat(json.readTree(invalido.body()).path("code").asText()).isEqualTo("TOKEN_DESBLOQUEO_INVALIDO");
        RESPUESTA.set(p->ResultadoDesbloqueo.EXPIRADA);
        var expirado=enviar("POST","{\"token\":\"sintetico\"}",null);
        assertThat(expirado.statusCode()).isEqualTo(410);
        assertThat(json.readTree(expirado.body()).path("code").asText()).isEqualTo("TOKEN_DESBLOQUEO_EXPIRADO");
    }
    @Test void dependenciaAusenteNuncaSePresentaComo204() throws Exception {
        RESPUESTA.set(p->{throw new ProteccionNoDisponibleException("proveedor ausente de test");});
        var r=enviar("POST","{\"token\":\"sintetico\"}",null);
        assertThat(r.statusCode()).isEqualTo(503);
        assertThat(json.readTree(r.body()).path("code").asText()).isEqualTo("NO_DISPONIBLE");
    }
    @Test void cuerpoInvalidoYJsonMalformadoNoExponenSecreto(CapturedOutput salida) throws Exception {
        String secreto="no-registrar-secreto-sintetico";
        for(String body:new String[]{"{}","{\"token\":null}","{\"token\":\"\"}","{\"token\":\""+secreto+"\""}) {
            var r=enviar("POST",body,null);
            assertThat(r.statusCode()).isEqualTo(400);
            assertThat(json.readTree(r.body()).path("code").asText()).isEqualTo("VALIDACION");
            assertThat(r.body()).doesNotContain(secreto);
        }
        assertThat(salida.getAll()).doesNotContain(secreto);
        assertThat(new DesbloquearRequest(secreto).toString()).doesNotContain(secreto);
        assertThat(new PruebaEnlace(secreto).toString()).doesNotContain(secreto);
    }
    @Test void getNoEsPublicoNiConsumePorPrevisualizacion() throws Exception {
        RESPUESTA.set(p->{throw new AssertionError("GET no debe consumir");});
        assertThat(enviar("GET","",null).statusCode()).isEqualTo(401);
    }
}
