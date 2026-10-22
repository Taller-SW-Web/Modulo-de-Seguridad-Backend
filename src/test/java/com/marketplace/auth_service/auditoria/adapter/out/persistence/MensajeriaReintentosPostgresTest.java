package com.marketplace.auth_service.auditoria.adapter.out.persistence;

import com.marketplace.auth_service.auditoria.adapter.out.correo.EnvioCorreoSmtp;
import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesAesGcm;
import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import com.marketplace.auth_service.auditoria.application.service.MensajeriaService;
import java.sql.DriverManager;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

/** TEC-INFRA-T004: calendario y límite persistidos, sin sleeps ni servicios remotos.
 * SMTP real no configurado; fallo de broker inducido solo en el puerto de test. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({MensajeriaReintentosPostgresTest.Config.class,OutboxJpaAdapter.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Testcontainers
class MensajeriaReintentosPostgresTest {
    @TestConfiguration(proxyBeanMethods=false)
    @EntityScan("com.marketplace.auth_service.auditoria.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.auditoria.adapter.out.persistence.repository")
    static class Config { }
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) throws Exception {
        PG.start();
        try(var c=DriverManager.getConnection(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())){
            BaseAuditoriaPostgres.cargarEsquema(c);BaseAuditoriaPostgres.migrar(c);
        }
        r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);
    }
    @Autowired OutboxPort outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    static class Reloj extends Clock {
        Instant ahora;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zona){return Clock.fixed(ahora,zona);}
        public Instant instant(){return ahora;}
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void calendarioEsDurableNoReclamaAntesDeTiempoYAgotaEnCuatroIntentos(boolean correo){
        var reloj=new Reloj();
        var cifrado=new CifradoMensajesAesGcm("test-reintentos",new byte[32]);
        var servicio=new MensajeriaService(outbox,cifrado,new EnvioCorreoSmtp(null,"test@example.test"),reloj);
        UUID id=UUID.randomUUID();
        new TransactionTemplate(manager).executeWithoutResult(s->{
            if(correo)servicio.guardarCorreo(new EntregaCorreo(id,"otp","sintetico@example.test","codigo-sintetico"));
            else servicio.guardarEvento(new EventoUsuario(id,"usuario.bloqueado",Map.of("usuarioId",UUID.randomUUID().toString())));
        });
        reloj.ahora=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).plusSeconds(1);
        String payloadOriginal=jdbc.queryForObject("select payload::text from outbox where id=?",String.class,id);
        Runnable ciclo=()->{
            if(correo)assertThat(servicio.procesarCorreos()).isZero();
            else assertThat(servicio.procesarEventos((topico,sobre)->{throw new IllegalStateException("broker de prueba no disponible");})).isZero();
        };
        int intentos=0;
        for(int segundos:new int[]{10,30,60}){
            ciclo.run();intentos++;
            Instant proximo=reloj.instant().plusSeconds(segundos);
            assertThat(jdbc.queryForObject("select intentos from outbox where id=?",Integer.class,id)).isEqualTo(intentos);
            assertThat(jdbc.queryForObject("select estado from outbox where id=?",String.class,id)).isEqualTo("PENDIENTE");
            Instant persistido=jdbc.queryForObject("select proximo_intento_en from outbox where id=?",
                    (rs,n)->rs.getObject(1,OffsetDateTime.class).toInstant(),id);
            assertThat(persistido).isEqualTo(proximo);
            assertThat(jdbc.queryForObject("select payload::text from outbox where id=?",String.class,id)).isEqualTo(payloadOriginal);
            reloj.ahora=proximo.minusNanos(1000);ciclo.run();
            assertThat(jdbc.queryForObject("select intentos from outbox where id=?",Integer.class,id)).isEqualTo(intentos);
            reloj.ahora=proximo;
        }
        ciclo.run();
        assertThat(jdbc.queryForObject("select intentos from outbox where id=?",Integer.class,id)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select estado from outbox where id=?",String.class,id)).isEqualTo("FALLIDO");
        assertThat(jdbc.queryForObject("select proximo_intento_en is null from outbox where id=?",Boolean.class,id)).isTrue();
        assertThat(jdbc.queryForObject("select payload::text from outbox where id=?",String.class,id)).isEqualTo("{}");
        reloj.ahora=reloj.ahora.plus(Duration.ofDays(1));ciclo.run();
        assertThat(jdbc.queryForObject("select intentos from outbox where id=?",Integer.class,id)).isEqualTo(4);
    }
}
