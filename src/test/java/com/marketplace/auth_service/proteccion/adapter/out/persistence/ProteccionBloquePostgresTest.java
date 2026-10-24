package com.marketplace.auth_service.proteccion.adapter.out.persistence;

import com.marketplace.auth_service.configuration.*;
import com.marketplace.auth_service.proteccion.application.dto.*;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import com.marketplace.auth_service.proteccion.application.port.out.*;
import com.marketplace.auth_service.proteccion.application.service.ProteccionService;
import com.marketplace.auth_service.proteccion.domain.exception.ProteccionNoDisponibleException;
import com.marketplace.auth_service.proteccion.domain.model.Bloqueo;
import com.marketplace.auth_service.proteccion.domain.model.TipoBloqueo;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({ProteccionBloquePostgresTest.Config.class,ProteccionConfig.class,ProteccionCriptografiaConfig.class,
        ProteccionJpaAdapter.class,UsuarioConfiguration.class,AutorizacionConfig.class})
@Testcontainers
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ProteccionBloquePostgresTest {
    @TestConfiguration(proxyBeanMethods=false)
    @EntityScan(basePackages={"com.marketplace.auth_service.proteccion.adapter.out.persistence.entity",
            "com.marketplace.auth_service.usuario.adapter.out.persistence.entity",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity"})
    @EnableJpaRepositories(basePackages={"com.marketplace.auth_service.proteccion.adapter.out.persistence.repository",
            "com.marketplace.auth_service.usuario.adapter.out.persistence.repository",
            "com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository"})
    static class Config { @Bean Clock reloj() { return RELOJ; } }

    static final Instant BASE=Instant.parse("2026-10-15T17:00:00Z");
    static final Reloj RELOJ=new Reloj();
    static class Reloj extends Clock {
        final AtomicReference<Instant> actual=new AtomicReference<>(BASE);
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return actual.get();}
    }
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        PG.start();
        try(var c=DriverManager.getConnection(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())) {
            try(var in=new ClassPathResource("proteccion/base-observada/schema.sql").getInputStream();var s=c.createStatement()) {
                s.execute(new String(in.readAllBytes(),StandardCharsets.UTF_8));
                s.execute("insert into rol(codigo,nombre) values('ADMIN_SISTEMA','Administrador')");
                // Dobles de dependencias SOLO de test; no son tablas productivas de otro dueño.
                s.execute("create table b04_token_doble(id uuid primary key,usuario uuid,revision uuid,hash text unique,expira timestamptz,usado boolean,proposito text)");
                s.execute("create table b04_efecto_doble(usuario uuid,revision uuid,accion text,primary key(revision,accion))");
            }
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migraciones-propuestas/V104__proteccion_racha_huella_revision.sql"));
        } catch(Exception e){throw new IllegalStateException("Fixture local B04 no disponible",e);}
        r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);
        r.add("AUTH_FALLOS_HMAC_KEY_ID",()->"test-b04");
        r.add("AUTH_FALLOS_HMAC_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    @Autowired ProteccionPort productivo;
    @Autowired CuentasProteccionPort cuentas;
    @Autowired PersistenciaProteccionPort persistencia;
    @Autowired HuellaProteccionPort huellas;
    @Autowired HashEnlaceProteccionPort hashes;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext contexto;
    UUID usuario;
    TokenDoble tokenDoble;
    EfectosDobles efectosDobles;
    TransactionTemplate tx(){return new TransactionTemplate(manager);}
    @BeforeEach void preparar() {
        RELOJ.actual.set(BASE);usuario=UUID.randomUUID();tokenDoble=new TokenDoble();efectosDobles=new EfectosDobles();
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado) values(?,?,'Cuenta','B04','ACTIVO')",
                usuario,usuario+"@example.test");
    }
    ProteccionPort aislado() {
        return new ProteccionService(persistencia,huellas,cuentas,tokenDoble,efectosDobles,hashes,RELOJ);
    }
    ContextoPeticion contexto(String ip){return new ContextoPeticion(ip,"B04",UUID.randomUUID());}
    ResultadoFallo fallo(ProteccionPort port,String secreto,String ip) {
        try(var entrada=new HuellaFallo(secreto.getBytes(StandardCharsets.UTF_8),usuario+"@example.test")){
            return tx().execute(status -> {
                cuentas.bloquearCatalogoAdministrador();
                var cuenta=cuentas.bloquear(usuario);
                return port.registrarFallo(cuenta,entrada,contexto(ip));
            });
        }
    }
    ResultadoFallo fallo(ProteccionPort p,String s){return fallo(p,s,"127.0.0.1");}
    CuentaSnapshot cuenta(){return cuentas.leer(usuario).orElseThrow();}
    long contar(String tabla){return jdbc.queryForObject("select count(*) from "+tabla+" where usuario"+(tabla.startsWith("b04_")?"":"_id")+"=?",Long.class,usuario);}
    void cinco(ProteccionPort p,int ciclo){for(int i=0;i<5;i++)fallo(p,"incorrecta-"+ciclo+"-"+i);}
    PruebaEnlace enlaceActual(){return new PruebaEnlace(tokenDoble.ultimo);}
    ResultadoDesbloqueo desbloquear(ProteccionPort p,PruebaEnlace prueba){return tx().execute(s->p.desbloquear(prueba,contexto("127.0.0.1")));}

    @Test void puertoUnicoSinProveedoresFicticiosYMutantesMandatory() {
        assertThat(contexto.getBeansOfType(ProteccionPort.class)).hasSize(1);
        assertThat(contexto.getBeansOfType(TokensProteccionPort.class)).isEmpty();
        assertThat(contexto.getBeansOfType(EfectosProteccionPort.class)).isEmpty();
        try(var entrada=new HuellaFallo(new byte[]{1},null)) {
            assertThatThrownBy(()->productivo.registrarFallo(cuenta(),entrada,contexto("127.0.0.1")))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
        assertThatThrownBy(()->productivo.completarLogin(usuario)).isInstanceOf(IllegalTransactionStateException.class);
    }
    @Test void repetidaNoSumaOtraIpNoDivideYElTiempoNoPurga() {
        assertThat(fallo(productivo,"igual").intentosFallidos()).isEqualTo(1);
        RELOJ.actual.set(BASE.plus(Duration.ofDays(20)));
        assertThat(fallo(productivo,"igual","127.0.0.2").estado()).isEqualTo(ResultadoFallo.Estado.REPETIDO);
        assertThat(cuenta().intentosFallidos()).isEqualTo(1);
        assertThat(contar("intento_login")).isEqualTo(2);
        assertThat(fallo(productivo," otra exacta ","127.0.0.3").intentosFallidos()).isEqualTo(2);
        assertThat(fallo(productivo,"otra exacta").intentosFallidos()).isEqualTo(3);
    }
    @Test void inexistentePendienteInactivaYBloqueadaRegistranSinSumar() {
        for(String estado:List.of("INACTIVO","PENDIENTE_VERIFICACION")) {
            jdbc.update("update usuario set estado=?::estado_cuenta where id=?",estado,usuario);
            assertThat(fallo(productivo,"nunca-suma").estado()).isEqualTo(ResultadoFallo.Estado.IGNORADO);
        }
        jdbc.update("update usuario set estado='ACTIVO' where id=?",usuario);
        tx().executeWithoutResult(s->{cuentas.bloquearCatalogoAdministrador();cuentas.bloquear(usuario);
            persistencia.guardarBloqueo(new Bloqueo(usuario,TipoBloqueo.MANUAL,null,"Fixture",BASE,UUID.randomUUID()));});
        assertThat(fallo(productivo,"nunca-suma").estado()).isEqualTo(ResultadoFallo.Estado.IGNORADO);
        try(var entrada=new HuellaFallo(new byte[]{1},"ausente@example.test")){
            tx().executeWithoutResult(s->assertThat(productivo.registrarFallo(null,entrada,contexto("127.0.0.1")).estado())
                    .isEqualTo(ResultadoFallo.Estado.IGNORADO));
        }
        assertThat(cuenta().intentosFallidos()).isZero();
        assertThat(contar("racha_fallo")).isZero();
    }
    @Test void clasificacionRealNoMutaYDistingueRenovacionDeLogin() {
        UUID revision=UUID.randomUUID();
        tx().executeWithoutResult(s->{cuentas.bloquear(usuario);persistencia.guardarBloqueo(
                new Bloqueo(usuario,TipoBloqueo.AUTOMATICO,BASE.plusSeconds(60),null,BASE,revision));});
        var auto=productivo.evaluar(cuenta(),BASE);
        assertThat(auto.permiteLogin()).isFalse();assertThat(auto.permiteRenovar()).isTrue();
        assertThat(productivo.evaluar(cuenta(),BASE.plusSeconds(60)).permiteLogin()).isTrue();
        assertThat(contar("bloqueo")).isEqualTo(1);
        tx().executeWithoutResult(s->{cuentas.bloquear(usuario);persistencia.guardarBloqueo(
                new Bloqueo(usuario,TipoBloqueo.MANUAL,null,"Fixture",BASE,UUID.randomUUID()));});
        assertThat(productivo.evaluar(cuenta(),BASE).permiteRenovar()).isFalse();
    }
    @Test void sinProveedorQuintoFalloRevierteTodoYConservaCuatro() {
        for(int i=0;i<4;i++)fallo(productivo,"distinta-"+i);
        assertThatThrownBy(()->fallo(productivo,"quinta")).isInstanceOf(ProteccionNoDisponibleException.class);
        assertThat(cuenta().intentosFallidos()).isEqualTo(4);
        assertThat(cuenta().bloqueosSeguidos()).isZero();
        assertThat(contar("huella_fallo")).isEqualTo(4);
        assertThat(contar("intento_login")).isEqualTo(4);
        assertThat(contar("bloqueo")).isZero();
    }
    @Test void aisladoProgresaUnoDosCuatroMinutosYSinVencimiento() {
        var p=aislado();
        for(int ciclo=1;ciclo<=4;ciclo++){
            cinco(p,ciclo);
            var evaluacion=p.evaluar(cuenta(),RELOJ.instant());
            assertThat(evaluacion.bloqueosSeguidos()).isEqualTo(ciclo);
            if(ciclo<4) {
                assertThat(evaluacion.bloqueadoHasta()).isEqualTo(RELOJ.instant().plusSeconds(60L<<(ciclo-1)));
                RELOJ.actual.set(evaluacion.bloqueadoHasta()); // Igualdad ya está vencido.
                assertThat(p.evaluar(cuenta(),RELOJ.instant()).intentosFallidos()).isZero();
            } else assertThat(evaluacion.bloqueadoHasta()).isNull();
        }
        assertThat(contar("b04_efecto_doble")).isEqualTo(4);
        assertThat(contar("b04_token_doble")).isEqualTo(4);
        assertThat(desbloquear(p,enlaceActual())).isEqualTo(ResultadoDesbloqueo.DESBLOQUEADA);
        assertThat(cuenta().bloqueosSeguidos()).isEqualTo(4);
    }
    @Test void quintoConcurrenteNoDuplicaBloqueoNiEfectosEnAislamiento() throws Exception {
        var p=aislado();for(int i=0;i<4;i++)fallo(p,"distinta-"+i);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var inicio=new CountDownLatch(1);
            var a=pool.submit(()->{inicio.await();return fallo(p,"quinta-a");});
            var c=pool.submit(()->{inicio.await();return fallo(p,"quinta-b");});
            inicio.countDown();
            var resultados=List.of(a.get(15,TimeUnit.SECONDS),c.get(15,TimeUnit.SECONDS));
            assertThat(resultados).extracting(ResultadoFallo::estado)
                    .containsExactlyInAnyOrder(ResultadoFallo.Estado.BLOQUEADO,ResultadoFallo.Estado.IGNORADO);
        }
        assertThat(contar("bloqueo")).isEqualTo(1);assertThat(contar("b04_efecto_doble")).isEqualTo(1);
        assertThat(contar("b04_token_doble")).isEqualTo(1);assertThat(cuenta().intentosFallidos()).isEqualTo(5);
    }
    @Test void falloCriticoRevierteContadoresBloqueoTokenYRegistroEnAislamiento() {
        var p=aislado();for(int i=0;i<4;i++)fallo(p,"distinta-"+i);
        efectosDobles.fallar=true;
        assertThatThrownBy(()->fallo(p,"quinta")).isInstanceOf(IllegalStateException.class);
        assertThat(cuenta().intentosFallidos()).isEqualTo(4);
        assertThat(contar("bloqueo")).isZero();assertThat(contar("b04_token_doble")).isZero();
        assertThat(contar("b04_efecto_doble")).isZero();assertThat(contar("intento_login")).isEqualTo(4);
    }
    @Test void desbloqueoAisladoReseteaRachaPeroNoSeguidosYRechazaSegundoConsumo() {
        var p=aislado();cinco(p,1);var enlace=enlaceActual();
        assertThat(desbloquear(p,enlace)).isEqualTo(ResultadoDesbloqueo.DESBLOQUEADA);
        assertThat(cuenta().bloqueosSeguidos()).isEqualTo(1);assertThat(cuenta().intentosFallidos()).isZero();
        assertThat(contar("huella_fallo")).isZero();assertThat(contar("bloqueo")).isZero();
        assertThat(desbloquear(p,enlace)).isEqualTo(ResultadoDesbloqueo.INVALIDA);
        fallo(p,"nuevo");
        tx().executeWithoutResult(s->{cuentas.bloquear(usuario);p.completarLogin(usuario);});
        assertThat(cuenta().bloqueosSeguidos()).isZero();assertThat(cuenta().intentosFallidos()).isZero();
    }
    @Test void enlaceReemplazadoManualYPropositoAjenoNoDesbloquean() {
        var p=aislado();cinco(p,1);var viejo=enlaceActual();
        RELOJ.actual.set(BASE.plusSeconds(60));cinco(p,2);var nuevo=enlaceActual();
        assertThat(desbloquear(p,viejo)).isEqualTo(ResultadoDesbloqueo.INVALIDA);
        jdbc.update("update b04_token_doble set proposito='RECUPERACION' where usuario=?",usuario);
        assertThat(desbloquear(p,nuevo)).isEqualTo(ResultadoDesbloqueo.INVALIDA);
        jdbc.update("update b04_token_doble set proposito='DESBLOQUEO_AUTOMATICO' where usuario=?",usuario);
        tx().executeWithoutResult(s->{cuentas.bloquearCatalogoAdministrador();cuentas.bloquear(usuario);
            persistencia.guardarBloqueo(new Bloqueo(usuario,TipoBloqueo.MANUAL,null,"Fixture",RELOJ.instant(),UUID.randomUUID()));});
        assertThat(desbloquear(p,nuevo)).isEqualTo(ResultadoDesbloqueo.INVALIDA);
        assertThat(p.evaluar(cuenta(),RELOJ.instant()).tipoBloqueo()).isEqualTo("MANUAL");
    }
    @Test void vencidoEsExpiradoYDesbloqueoCriticoFallidoRevierteConsumo() {
        var p=aislado();cinco(p,1);var enlace=enlaceActual();
        efectosDobles.fallar=true;
        assertThatThrownBy(()->desbloquear(p,enlace)).isInstanceOf(IllegalStateException.class);
        assertThat(contar("bloqueo")).isEqualTo(1);
        assertThat(tokenDoble.localizar(hashes.calcular(enlace)).orElseThrow().usado()).isFalse();
        efectosDobles.fallar=false;
        RELOJ.actual.set(BASE.plus(Duration.ofMinutes(30)));
        assertThat(desbloquear(p,enlace)).isEqualTo(ResultadoDesbloqueo.EXPIRADA);
    }
    @Test void reinicioDeServicioConservaDeduplicacionPersistida() {
        fallo(productivo,"secreto-exacto");
        var nuevo=aislado();
        assertThat(fallo(nuevo,"secreto-exacto").estado()).isEqualTo(ResultadoFallo.Estado.REPETIDO);
        assertThat(cuenta().intentosFallidos()).isEqualTo(1);
    }

    @Test void dobleConsumoConcurrenteSoloDesbloqueaUnaVezEnAislamiento() throws Exception {
        var p=aislado();cinco(p,1);var enlace=enlaceActual();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var inicio=new CountDownLatch(1);
            var a=pool.submit(()->{inicio.await();return desbloquear(p,enlace);});
            var c=pool.submit(()->{inicio.await();return desbloquear(p,enlace);});
            inicio.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),c.get(15,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(ResultadoDesbloqueo.DESBLOQUEADA,ResultadoDesbloqueo.INVALIDA);
        }
        assertThat(contar("b04_efecto_doble")).isEqualTo(2); // bloqueo y desbloqueo.
    }

    /** Dobles transaccionales exclusivos del test: NO acreditan #20 ni Christian. */
    class TokenDoble implements TokensProteccionPort {
        volatile String ultimo;
        public Optional<TokenDesbloqueo> localizar(String hash) {
            return jdbc.query("select * from b04_token_doble where hash=?", (rs,n)->new TokenDesbloqueo(
                    rs.getObject("id",UUID.class),rs.getObject("usuario",UUID.class),rs.getString("proposito"),
                    rs.getObject("revision",UUID.class),rs.getTimestamp("expira").toInstant(),rs.getBoolean("usado")),hash).stream().findFirst();
        }
        public EnlaceEmitido emitir(UUID u,UUID revision,Duration ttl) {
            UUID id=UUID.randomUUID();ultimo=id.toString();Instant vence=RELOJ.instant().plus(ttl);
            jdbc.update("insert into b04_token_doble values(?,?,?,?,?,false,'DESBLOQUEO_AUTOMATICO')",
                    id,u,revision,hashes.calcular(new PruebaEnlace(ultimo)),java.sql.Timestamp.from(vence));
            return new EnlaceEmitido(id,ultimo,vence);
        }
        public boolean consumir(UUID id,UUID u,UUID revision,Instant ahora) {
            return jdbc.update("update b04_token_doble set usado=true where id=? and usuario=? and revision=? and not usado and expira>?",
                    id,u,revision,java.sql.Timestamp.from(ahora))==1;
        }
        public void invalidarPrevios(UUID u){jdbc.update("update b04_token_doble set usado=true where usuario=?",u);}
    }
    class EfectosDobles implements EfectosProteccionPort {
        boolean fallar;
        public void bloqueada(CuentaSnapshot c,UUID revision,Instant hasta,EnlaceEmitido enlace,ContextoPeticion p,Instant ahora) {
            escribir(c,revision,"BLOQUEADA");
        }
        public void desbloqueada(CuentaSnapshot c,UUID revision,ContextoPeticion p,Instant ahora) {
            escribir(c,revision,"DESBLOQUEADA");
        }
        public void desbloqueadaPorRestablecimiento(CuentaSnapshot c,UUID revision,ContextoPeticion p,Instant ahora) {
            escribir(c,revision,"DESBLOQUEADA_RESTABLECIMIENTO");
        }
        void escribir(CuentaSnapshot c,UUID r,String accion) {
            jdbc.update("insert into b04_efecto_doble values(?,?,?)",c.usuarioId(),r,accion);
            if(fallar) throw new IllegalStateException("Fallo crítico sintético");
        }
    }
}
