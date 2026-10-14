package com.marketplace.auth_service.proteccion.adapter.out.persistence;

import com.marketplace.auth_service.proteccion.adapter.out.crypto.HuellaProteccionJca;
import com.marketplace.auth_service.proteccion.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"}, showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import(ProteccionJpaAdapter.class)
@Testcontainers
class ProteccionJpaPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16.6");
    private static final Instant AHORA=Instant.parse("2026-10-14T05:00:00Z");
    private static final UUID LEGACY=UUID.fromString("00000000-0000-0000-0000-000000000071");

    @DynamicPropertySource static void baseDePrueba(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        try(var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())) {
            for(String archivo:new String[]{"V1__usuario_y_roles.sql","V2__credenciales_otp_tokens.sql",
                    "V3__sesion_y_bloqueo.sql","V4__auditoria_y_integracion.sql"}) {
                try(var entrada=new ClassPathResource("proteccion/base-historica/"+archivo).getInputStream();
                    var sentencia=c.createStatement()) {
                    sentencia.execute(new String(entrada.readAllBytes(),StandardCharsets.UTF_8));
                }
            }
            try(var sentencia=c.createStatement()) {
                sentencia.execute("insert into usuario(id,correo,nombres,apellidos,estado) "
                        +"values ('"+LEGACY+"','historico@ejemplo.test','Historico','Prueba','ACTIVO')");
                sentencia.execute("insert into bloqueo(usuario_id,tipo,bloqueado_hasta,motivo,creado_en) "
                        +"values ('"+LEGACY+"','AUTOMATICO',null,null,'2020-01-01T00:00:00Z')");
            }
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migraciones-propuestas/V104__proteccion_racha_huella_revision.sql"));
        } catch(Exception e) { throw new IllegalStateException("No se pudo preparar PostgreSQL de prueba de protección",e); }
        registro.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username",POSTGRES::getUsername);
        registro.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired ProteccionJpaAdapter persistencia;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    UUID usuario,rachaId;
    @BeforeEach void fixture() {
        usuario=UUID.randomUUID(); rachaId=UUID.randomUUID();
        insertarUsuario(usuario);
    }
    private void insertarUsuario(UUID id) {
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado) values (?,?,?,?,'ACTIVO')",
                id,id+"@ejemplo.test","Cliente","Prueba");
    }
    private RachaFallo crearRacha() {
        var r=new RachaFallo(usuario,rachaId,"k1",AHORA);
        persistencia.crearRacha(r);
        return r;
    }
    private HuellaFallo huella(String h) { return new HuellaFallo(usuario,rachaId,h.repeat(64),AHORA); }

    @Test void persisteYBloqueaRachaSinTocarContadoresDeCuenta() {
        var r=crearRacha();
        assertThat(persistencia.buscarRacha(usuario)).contains(r);
        assertThat(persistencia.bloquearRacha(usuario)).contains(r);
        assertThat(jdbc.queryForObject("select intentos_fallidos_consecutivos from usuario where id=?",Integer.class,usuario)).isZero();
    }
    @Test void huellaDuplicadaNoInsertaNiAbortaLaTransaccion() {
        crearRacha();
        assertThat(persistencia.insertarHuellaSiNueva(huella("a"))).isTrue();
        assertThat(persistencia.insertarHuellaSiNueva(huella("a"))).isFalse();
        assertThat(persistencia.insertarHuellaSiNueva(huella("b"))).isTrue();
        assertThat(persistencia.contarHuellas(usuario,rachaId)).isEqualTo(2);
    }
    @Test void noAceptaHuellaDeOtraRachaODelUsuarioAjeno() {
        crearRacha();
        var h=new HuellaFallo(usuario,UUID.randomUUID(),"a".repeat(64),AHORA);
        assertThatThrownBy(() -> persistencia.insertarHuellaSiNueva(h))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("fk_huella_fallo_racha");
    }
    @Test void formatoHmacYKeyIdSeProtegenTambienEnLaBase() {
        crearRacha();
        assertThatThrownBy(() -> jdbc.update("insert into huella_fallo(usuario_id,racha_id,huella_hmac) values (?,?,?)",
                usuario,rachaId,"z".repeat(64))).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void keyIdVacioNoCreaRacha() {
        assertThatThrownBy(() -> jdbc.update("insert into racha_fallo(usuario_id,racha_id,hash_key_id) values (?,?,' ')",
                usuario,rachaId)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void reiniciarRenuevaIdentidadYLimpiaSoloHuellasPropias() {
        crearRacha(); persistencia.insertarHuellaSiNueva(huella("a"));
        var nueva=new RachaFallo(usuario,UUID.randomUUID(),"k2",AHORA.plusSeconds(1));
        persistencia.reiniciarRacha(nueva);
        assertThat(persistencia.buscarRacha(usuario)).contains(nueva);
        assertThat(persistencia.contarHuellas(usuario,rachaId)).isZero();
        assertThat(persistencia.insertarHuellaSiNueva(new HuellaFallo(usuario,nueva.rachaId(),"a".repeat(64),AHORA.plusSeconds(1)))).isTrue();
    }
    @Test void noPermiteReiniciarConLaMismaRachaYPepperDistinto() {
        crearRacha();
        assertThatThrownBy(() -> persistencia.reiniciarRacha(new RachaFallo(usuario,rachaId,"k2",AHORA)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void hmacPermaneceDeduplicableTrasReconstruirProveedorYLeerRacha() {
        var r=crearRacha();
        byte[] prueba="contraseña-de-prueba".getBytes(StandardCharsets.UTF_8);
        var p=new HuellaProteccionJca("k1",Map.of("k1",new byte[32]));
        assertThat(persistencia.insertarHuellaSiNueva(p.generar(r,prueba,AHORA))).isTrue();
        var reinicio=new HuellaProteccionJca("k1",Map.of("k1",new byte[32]));
        var historica=persistencia.buscarRacha(usuario).orElseThrow();
        assertThat(persistencia.insertarHuellaSiNueva(reinicio.generar(historica,prueba,AHORA.plusSeconds(900000)))).isFalse();
        assertThat(persistencia.contarHuellas(usuario,rachaId)).isEqualTo(1);
    }
    @Test void preservaHistoricoYLeAsignaRevisionSinCambiarTipoOHasta() {
        var b=persistencia.buscarBloqueo(LEGACY).orElseThrow();
        assertThat(b.revision()).isNotNull();
        assertThat(b.tipo()).isEqualTo(TipoBloqueo.AUTOMATICO);
        assertThat(b.bloqueadoHasta()).isNull();
        assertThat(b.creadoEn()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
    }
    @Test void bloqueoPosteriorCambiaRevisionYNoAceptaEliminarLaAnterior() {
        var primero=new Bloqueo(usuario,TipoBloqueo.AUTOMATICO,AHORA.plusSeconds(60),null,AHORA,UUID.randomUUID());
        var segundo=new Bloqueo(usuario,TipoBloqueo.AUTOMATICO,null,null,AHORA.plusSeconds(1),UUID.randomUUID());
        persistencia.guardarBloqueo(primero);
        persistencia.guardarBloqueo(segundo);
        assertThat(persistencia.bloquearBloqueo(usuario)).contains(segundo);
        assertThat(persistencia.eliminarBloqueo(usuario,primero.revision())).isFalse();
        assertThat(persistencia.eliminarBloqueo(usuario,segundo.revision())).isTrue();
        assertThat(persistencia.buscarBloqueo(usuario)).isEmpty();
    }
    @Test void vistaProyectaAutomaticoVencidoSinMutarEstadoBase() {
        var relojDb=jdbc.queryForObject("select now()",java.time.OffsetDateTime.class).toInstant();
        persistencia.guardarBloqueo(new Bloqueo(usuario,TipoBloqueo.AUTOMATICO,relojDb.minusSeconds(1),null,relojDb.minusSeconds(60),UUID.randomUUID()));
        assertThat(jdbc.queryForObject("select estado::text from usuario_estado_efectivo where usuario_id=?",String.class,usuario)).isEqualTo("ACTIVO");
        persistencia.guardarBloqueo(new Bloqueo(usuario,TipoBloqueo.MANUAL,null,"Soporte",relojDb,UUID.randomUUID()));
        assertThat(jdbc.queryForObject("select estado::text from usuario_estado_efectivo where usuario_id=?",String.class,usuario)).isEqualTo("BLOQUEADO");
        assertThat(jdbc.queryForObject("select estado::text from usuario where id=?",String.class,usuario)).isEqualTo("ACTIVO");
    }
    @Test void intentosSinCuentaYRepetidosSeRegistranSinContrasena() {
        var primero=new IntentoLogin(UUID.randomUUID(),null,"noexiste@ejemplo.test",ResultadoLogin.FALLO,"::1","Prueba",AHORA);
        persistencia.registrarIntento(primero);
        persistencia.registrarIntento(new IntentoLogin(UUID.randomUUID(),usuario,null,ResultadoLogin.FALLO,"127.0.0.1",null,AHORA));
        persistencia.registrarIntento(new IntentoLogin(UUID.randomUUID(),usuario,null,ResultadoLogin.FALLO,"127.0.0.2",null,AHORA.plusSeconds(1)));
        assertThat(jdbc.queryForObject("select count(*) from intento_login where usuario_id=?",Integer.class,usuario)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from intento_login where id=? and usuario_id is null",Integer.class,primero.id())).isEqualTo(1);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void escriturasYLocksNecesitanTransaccionDelCoordinador() {
        var r=new RachaFallo(usuario,rachaId,"k1",AHORA);
        assertThatThrownBy(() -> persistencia.crearRacha(r)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> persistencia.bloquearRacha(usuario)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> persistencia.registrarIntento(new IntentoLogin(UUID.randomUUID(),usuario,null,ResultadoLogin.FALLO,null,null,AHORA)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void mismoFalloConcurrenteSeGuardaUnaVezConLockDeRacha() throws Exception {
        var tx=new TransactionTemplate(transacciones);
        tx.executeWithoutResult(s -> crearRacha());
        var inicio=new CountDownLatch(1);
        try(var ejecutor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> trabajo=() -> {
                inicio.await();
                return tx.execute(s -> {
                    persistencia.bloquearRacha(usuario).orElseThrow();
                    return persistencia.insertarHuellaSiNueva(huella("a"));
                });
            };
            var uno=ejecutor.submit(trabajo); var dos=ejecutor.submit(trabajo); inicio.countDown();
            assertThat(java.util.List.of(uno.get(15,TimeUnit.SECONDS),dos.get(15,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
        }
        assertThat(persistencia.contarHuellas(usuario,rachaId)).isEqualTo(1);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void rollbackNoDejaIntentoNiHuellaParcial() {
        var tx=new TransactionTemplate(transacciones);
        tx.executeWithoutResult(s -> crearRacha());
        UUID intento=UUID.randomUUID();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            persistencia.insertarHuellaSiNueva(huella("a"));
            persistencia.registrarIntento(new IntentoLogin(intento,usuario,null,ResultadoLogin.FALLO,null,null,AHORA));
            throw new IllegalStateException("fallo técnico de prueba");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(persistencia.contarHuellas(usuario,rachaId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from intento_login where id=?",Integer.class,intento)).isZero();
    }
    @Test void rlsNuevasTablasEstaActivadaSinInventarGrantsRuntime() {
        assertThat(jdbc.queryForObject("select count(*) from pg_class where relname in ('racha_fallo','huella_fallo') and relrowsecurity",Integer.class)).isEqualTo(2);
    }
}
