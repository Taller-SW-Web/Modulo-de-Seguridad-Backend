package com.marketplace.auth_service.proteccion.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.auditoria.adapter.in.transaction.AuditoriaTransaccional;
import com.marketplace.auth_service.auditoria.adapter.out.correo.EnvioCorreoSmtp;
import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesAesGcm;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.*;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.repository.OutboxJpaRepository;
import com.marketplace.auth_service.auditoria.adapter.out.transaccion.TareasTrasCommitSpring;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.out.*;
import com.marketplace.auth_service.auditoria.application.service.*;
import com.marketplace.auth_service.configuration.*;
import com.marketplace.auth_service.credencial.adapter.out.persistence.CredencialesJpaAdapter;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.mfa.adapter.out.persistence.MfaJpaAdapter;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.mfa.domain.model.*;
import com.marketplace.auth_service.proteccion.application.dto.*;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import com.marketplace.auth_service.proteccion.application.port.out.CuentasProteccionPort;
import com.marketplace.auth_service.proteccion.application.port.out.PersistenciaProteccionPort;
import com.marketplace.auth_service.proteccion.domain.model.Bloqueo;
import com.marketplace.auth_service.proteccion.domain.model.TipoBloqueo;
import com.marketplace.auth_service.sesion.adapter.out.persistence.SesionJpaAdapter;
import com.marketplace.auth_service.sesion.application.dto.*;
import com.marketplace.auth_service.sesion.application.port.in.*;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** EP-07-T006: proveedores reales; sin broker, worker programado ni correo entregado ficticio. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({ProteccionComposicionPostgresTest.Config.class,UsuarioConfiguration.class,AutorizacionConfig.class,
        CredencialesConfig.class,CredencialesJpaAdapter.class,TokensUnUsoConfig.class,
        ProteccionConfig.class,ProteccionCriptografiaConfig.class,ProteccionJpaAdapter.class,EfectosConfig.class,
        SesionConfig.class,SesionJpaAdapter.class,IdentidadConfig.class,LoginConfig.class,
        MfaConfig.class,MfaCriptografiaConfig.class,MfaJpaAdapter.class,AuditoriaJpaAdapter.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Testcontainers
class ProteccionComposicionPostgresTest {
    static class Reloj extends Clock {
        volatile Instant ahora;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zona){return Clock.fixed(ahora,zona);}
        public Instant instant(){return ahora;}
        void avanzar(long segundos){ahora=ahora.plusSeconds(segundos);}
    }
    @TestConfiguration(proxyBeanMethods=false)
    @EntityScan("com.marketplace.auth_service")
    @EnableJpaRepositories("com.marketplace.auth_service")
    static class Config {
        @Bean Reloj reloj(){var r=new Reloj();r.ahora=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);return r;}
        @Bean CifradoMensajesPort cifrado(){return new CifradoMensajesAesGcm("test-t006",new byte[32]);}
        @Bean OutboxPort outbox(OutboxJpaRepository filas){return new OutboxJpaAdapter(filas);}
        @Bean AuditoriaPort auditoria(AuditoriaPersistenciaPort filas,PlatformTransactionManager tx){
            return new AuditoriaTransaccional(new AuditoriaService(filas,new TareasTrasCommitSpring(tx)));
        }
        @Bean MensajeriaService mensajeria(OutboxPort outbox,CifradoMensajesPort cifrado,Clock reloj){
            return new MensajeriaService(outbox,cifrado,new EnvioCorreoSmtp(null,"test@example.test"),reloj);
        }
    }
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) throws Exception {
        PG.start();
        try(var c=DriverManager.getConnection(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword());var s=c.createStatement()){
            try(var in=new ClassPathResource("sesion/base-observada/schema.sql").getInputStream()){
                s.execute(new String(in.readAllBytes(),StandardCharsets.UTF_8));
            }
            c.setAutoCommit(false);
            try(var migraciones=Files.list(Path.of("src/main/resources/db/migration"))){
                for(var p:migraciones.filter(p->p.toString().endsWith(".sql")).sorted().toList())s.execute(Files.readString(p));
            }
            c.commit();s.execute("insert into rol(codigo,nombre) values('CLIENTE','Cliente'),('ADMIN_SISTEMA','Administrador')");c.commit();
        }
        Path comunes=Path.of("build/test-fixtures/t006-comunes.txt").toAbsolutePath();
        Files.createDirectories(comunes.getParent());Files.writeString(comunes,"ComunDePrueba8!\n");
        r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);r.add("g7.password.comunes.archivo",comunes::toString);
        r.add("AUTH_FALLOS_HMAC_KEY_ID",()->"test-t006");r.add("AUTH_FALLOS_HMAC_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
        r.add("AUTH_OTP_HMAC_KEY_ID",()->"test-t006");r.add("AUTH_OTP_HMAC_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
        r.add("identidad.issuer",()->"https://t006.example.test/auth");
        r.add("identidad.clave-privada",()->ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }
    @Autowired ProteccionPort proteccion;
    @Autowired CuentasProteccionPort cuentas;
    @Autowired PersistenciaProteccionPort persistencia;
    @Autowired LoginPort login;
    @Autowired SesionesPort sesiones;
    @Autowired PasswordHasherPort hasher;
    @Autowired CifradoMensajesPort cifrado;
    @Autowired MensajeriaService mensajeria;
    @Autowired PersistenciaMfaPort mfa;
    @Autowired CriptografiaMfaPort cryptoMfa;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired Reloj reloj;
    UUID usuario;
    String correo;
    static final String CLAVE="Clave-Segura-2026!";
    TransactionTemplate tx(){return new TransactionTemplate(manager);}
    ContextoPeticion contexto(){return new ContextoPeticion("127.0.0.1","t006",UUID.randomUUID());}
    @BeforeEach void preparar(){
        reloj.ahora=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        usuario=UUID.randomUUID();correo=usuario+"@example.test";
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado,correo_verificado) values(?,?,'Cuenta','L','ACTIVO',true)",usuario,correo);
        jdbc.update("insert into usuario_rol(usuario_id,rol_codigo) values(?,'CLIENTE')",usuario);
        jdbc.update("insert into credencial(usuario_id,hash_contrasena,ultimo_cambio,version) values(?,?,now(),0)",usuario,hasher.generar(CLAVE).valor());
    }
    @AfterEach void retirarFallos(){
        jdbc.execute("alter table outbox drop constraint if exists t006_fallo");
        jdbc.execute("alter table auditoria_seguridad drop constraint if exists t006_fallo");
    }
    ResultadoFallo fallo(String secreto){
        try(var entrada=new HuellaFallo(secreto.getBytes(StandardCharsets.UTF_8),correo)){
            return tx().execute(s->{cuentas.bloquearCatalogoAdministrador();
                return proteccion.registrarFallo(cuentas.bloquear(usuario),entrada,contexto());});
        }
    }
    void fallos(int cantidad,int ciclo){for(int i=0;i<cantidad;i++)fallo("incorrecta-"+ciclo+"-"+i);}
    long contar(String tabla){return jdbc.queryForObject("select count(*) from "+tabla+" where usuario_id=?",Long.class,usuario);}
    long auditorias(String accion){return jdbc.queryForObject("select count(*) from auditoria_seguridad where objetivo_usuario_id=? and accion=?",Long.class,usuario,accion);}
    List<Map<String,Object>> correos(String topico){
        return jdbc.queryForList("select id,intentos,payload::text as carga from outbox where topico=?",topico).stream().filter(f->{
            try{return cifrado.descifrar(new ObjectMapper().readTree((String)f.get("carga")).path("destino").asText()).equals(correo);}
            catch(Exception e){throw new IllegalStateException(e);}}).toList();
    }
    long eventos(String topico){return jdbc.queryForObject("select count(*) from outbox where topico=? and payload->>'usuarioId'=?",Long.class,topico,usuario.toString());}
    String enlace() throws Exception {
        String carga=(String)correos("correo.cuenta_bloqueada").getLast().get("carga");
        String contenido=cifrado.descifrar(new ObjectMapper().readTree(carga).path("contenido").asText());
        var matcher=java.util.regex.Pattern.compile("token=([^\\s]+)").matcher(contenido);
        assertThat(matcher.find()).isTrue();return URLDecoder.decode(matcher.group(1),StandardCharsets.UTF_8);
    }
    ResultadoDesbloqueo desbloquear(String enlace){return proteccion.desbloquear(new PruebaEnlace(enlace),contexto());}
    @Test void quintoLoginConcurrenteConfirmaUnBloqueoTokenAuditoriaEventoYCorreo() throws Exception {
        for(int i=0;i<4;i++)assertThat(login.iniciar(correo,"fallo-"+i,contexto())).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);
        try(var pool=Executors.newFixedThreadPool(2)){
            var inicio=new CountDownLatch(1);
            var a=pool.submit(()->{inicio.await();return login.iniciar(correo,"quinto-a",contexto());});
            var b=pool.submit(()->{inicio.await();return login.iniciar(correo,"quinto-b",contexto());});inicio.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);
            assertThat(b.get(20,TimeUnit.SECONDS)).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);
        }
        assertThat(contar("bloqueo")).isEqualTo(1);assertThat(contar("token_un_uso")).isEqualTo(1);
        assertThat(cuentas.leer(usuario).orElseThrow().intentosFallidos()).isEqualTo(5);
        assertThat(auditorias("CUENTA_BLOQUEADA")).isEqualTo(1);assertThat(eventos("usuario.bloqueado")).isEqualTo(1);
        assertThat(correos("correo.cuenta_bloqueada")).hasSize(1);
        String secreto=enlace();
        assertThat((String)correos("correo.cuenta_bloqueada").getFirst().get("carga")).doesNotContain(correo,secreto);
        assertThat(jdbc.queryForObject("select payload::text from outbox where topico='usuario.bloqueado' and payload->>'usuarioId'=?",String.class,usuario.toString())).doesNotContain(secreto,"token=");
    }
    @ParameterizedTest @ValueSource(strings={"auditoria_seguridad","outbox"})
    void falloCriticoRevierteQuintoTokenContadoresYTodosLosEfectos(String tabla){
        fallos(4,0);
        jdbc.execute("alter table "+tabla+" add constraint t006_fallo check("+(tabla.equals("outbox")?"topico <> 'correo.cuenta_bloqueada'":"accion <> 'CUENTA_BLOQUEADA'")+") not valid");
        assertThatThrownBy(()->fallo("quinto")).isInstanceOf(RuntimeException.class);
        assertThat(cuentas.leer(usuario).orElseThrow().intentosFallidos()).isEqualTo(4);
        assertThat(contar("bloqueo")).isZero();assertThat(contar("token_un_uso")).isZero();
        assertThat(contar("huella_fallo")).isEqualTo(4);assertThat(contar("intento_login")).isEqualTo(4);
        assertThat(auditorias("CUENTA_BLOQUEADA")).isZero();assertThat(eventos("usuario.bloqueado")).isZero();
        assertThat(correos("correo.cuenta_bloqueada")).isEmpty();
    }
    @Test void dobleConsumoConcurrenteConfirmaUnaSolaLiberacionYEfectos() throws Exception {
        fallos(5,0);String secreto=enlace();
        try(var pool=Executors.newFixedThreadPool(2)){
            var inicio=new CountDownLatch(1);
            var a=pool.submit(()->{inicio.await();return desbloquear(secreto);});
            var b=pool.submit(()->{inicio.await();return desbloquear(secreto);});inicio.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(ResultadoDesbloqueo.DESBLOQUEADA,ResultadoDesbloqueo.INVALIDA);
        }
        assertThat(contar("bloqueo")).isZero();assertThat(auditorias("CUENTA_DESBLOQUEADA")).isEqualTo(1);
        assertThat(eventos("usuario.desbloqueado")).isEqualTo(1);assertThat(correos("correo.cuenta_desbloqueada")).hasSize(1);
        assertThat(cuentas.leer(usuario).orElseThrow().bloqueosSeguidos()).isEqualTo(1);
    }
    @Test void falloAuditoriaDesbloqueoRevierteConsumoYRacha() throws Exception {
        fallos(5,0);String secreto=enlace();
        jdbc.execute("alter table auditoria_seguridad add constraint t006_fallo check(accion <> 'CUENTA_DESBLOQUEADA') not valid");
        assertThatThrownBy(()->desbloquear(secreto)).isInstanceOf(RuntimeException.class);
        assertThat(contar("bloqueo")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select usado from token_un_uso where usuario_id=?",Boolean.class,usuario)).isFalse();
        assertThat(cuentas.leer(usuario).orElseThrow().intentosFallidos()).isEqualTo(5);
        assertThat(eventos("usuario.desbloqueado")).isZero();assertThat(correos("correo.cuenta_desbloqueada")).isEmpty();
    }
    @Test void cuatroBloqueosVencenEnLimiteExactoYReemplazanEnlace() throws Exception {
        String anterior=null;
        for(int ciclo=0;ciclo<4;ciclo++){
            fallos(5,ciclo);var b=persistencia.buscarBloqueo(usuario).orElseThrow();
            if(anterior!=null)assertThat(desbloquear(anterior)).isEqualTo(ResultadoDesbloqueo.INVALIDA);
            // Hay varios correos sin orden SQL: se obtiene el enlace correspondiente a la revisión vigente por su hash.
            anterior=enlaceVigente();
            assertThat(cuentas.leer(usuario).orElseThrow().bloqueosSeguidos()).isEqualTo(ciclo+1);
            if(ciclo<3){
                assertThat(b.bloqueadoHasta()).isEqualTo(reloj.instant().plusSeconds(60L<<ciclo));
                reloj.ahora=b.bloqueadoHasta().minusNanos(1);
                assertThat(proteccion.evaluar(cuentas.leer(usuario).orElseThrow(),reloj.instant()).permiteLogin()).isFalse();
                reloj.ahora=b.bloqueadoHasta();
                assertThat(proteccion.evaluar(cuentas.leer(usuario).orElseThrow(),reloj.instant()).permiteLogin()).isTrue();
            }else{
                assertThat(b.bloqueadoHasta()).isNull();reloj.avanzar(1801);
                assertThat(proteccion.evaluar(cuentas.leer(usuario).orElseThrow(),reloj.instant()).permiteLogin()).isFalse();
                assertThat(desbloquear(anterior)).isEqualTo(ResultadoDesbloqueo.EXPIRADA);
            }
        }
        assertThat(auditorias("CUENTA_BLOQUEADA")).isEqualTo(4);assertThat(contar("token_un_uso")).isEqualTo(4);
    }
    String enlaceVigente() throws Exception {
        String hash=jdbc.queryForObject("select hash_token from token_un_uso where usuario_id=? and not usado",String.class,usuario);
        for(var fila:correos("correo.cuenta_bloqueada")){
            String cuerpo=cifrado.descifrar(new ObjectMapper().readTree((String)fila.get("carga")).path("contenido").asText());
            var m=java.util.regex.Pattern.compile("token=([^\\s]+)").matcher(cuerpo);if(!m.find())continue;
            String candidato=URLDecoder.decode(m.group(1),StandardCharsets.UTF_8);
            if(new com.marketplace.auth_service.proteccion.adapter.out.crypto.HashEnlaceProteccionJca()
                    .calcular(new PruebaEnlace(candidato)).equals(hash))return candidato;
        }throw new IllegalStateException("Falta correo de revisión vigente");
    }
    @Test void automaticoPermiteRefreshPrevioPeroNiegaLoginYLoginAlVencerResetea(){
        var inicial=((ResultadoLogin.Iniciada)login.iniciar(correo,CLAVE,contexto())).sesion();
        fallos(5,0);
        assertThat(login.iniciar(correo,CLAVE,contexto())).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);
        assertThat(sesiones.renovar(new SecretoRefresco(inicial.refreshToken()))).isInstanceOf(RenovacionResultado.Renovada.class);
        assertThat(contar("sesion_familia")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select revocada from sesion_familia where usuario_id=?",Boolean.class,usuario)).isFalse();
        reloj.avanzar(60);assertThat(login.iniciar(correo,CLAVE,contexto())).isInstanceOf(ResultadoLogin.Iniciada.class);
        assertThat(cuentas.leer(usuario).orElseThrow().bloqueosSeguidos()).isZero();
        assertThat(cuentas.leer(usuario).orElseThrow().intentosFallidos()).isZero();assertThat(contar("huella_fallo")).isZero();
    }
    @Test void manualFixtureNiegaLoginRefreshYEnlaceSinRevocarFamilia() throws Exception {
        var inicial=((ResultadoLogin.Iniciada)login.iniciar(correo,CLAVE,contexto())).sesion();fallos(5,0);String secreto=enlace();
        tx().executeWithoutResult(s->{cuentas.bloquearCatalogoAdministrador();cuentas.bloquear(usuario);
            persistencia.guardarBloqueo(new Bloqueo(usuario,TipoBloqueo.MANUAL,null,"Fixture de estado histórico",reloj.instant(),UUID.randomUUID()));});
        assertThat(login.iniciar(correo,CLAVE,contexto())).isEqualTo(ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);
        assertThat(sesiones.renovar(new SecretoRefresco(inicial.refreshToken())))
                .isEqualTo(new RenovacionResultado.Rechazada(MotivoRechazo.CUENTA_NO_DISPONIBLE,usuario,
                    jdbc.queryForObject("select id from sesion_familia where usuario_id=?",UUID.class,usuario)));
        assertThat(desbloquear(secreto)).isEqualTo(ResultadoDesbloqueo.INVALIDA);
        assertThat(jdbc.queryForObject("select revocada from sesion_familia where usuario_id=?",Boolean.class,usuario)).isFalse();
    }
    @Test void soloMfaCompletoReseteaNoDesafioNiCodigoErroneo(){
        fallos(3,0);jdbc.update("update usuario set mfa_habilitado=true where id=?",usuario);
        var desafio=((ResultadoLogin.DesafioRequerido)login.iniciar(correo,CLAVE,contexto())).desafio();
        assertThat(cuentas.leer(usuario).orElseThrow().intentosFallidos()).isEqualTo(3);assertThat(contar("sesion_familia")).isZero();
        // Fixture de un OTP auténtico: generación/HMAC/persistencia reales. No acredita entrega EMAIL (bloque B separado).
        String codigo=tx().execute(s->{cuentas.bloquear(usuario);
            var d=mfa.buscarDesafioPorHash(cryptoMfa.hashDesafio(desafio.challengeToken())).orElseThrow();
            var contexto=new ContextoOtp(usuario,d.id(),UUID.randomUUID());var generado=cryptoMfa.generarOtp(contexto);
            mfa.guardarOtp(Otp.nuevo(contexto,generado.hashCodigo(),generado.hashKeyId(),CanalOtp.EMAIL,correo,reloj.instant()));return generado.codigo();});
        String incorrecto=codigo.equals("000000")?"111111":"000000";
        assertThat(login.completarMfa(desafio.challengeToken(),incorrecto,contexto())).isInstanceOf(ResultadoLogin.CodigoInvalido.class);
        assertThat(cuentas.leer(usuario).orElseThrow().intentosFallidos()).isEqualTo(3);
        assertThat(login.completarMfa(desafio.challengeToken(),codigo,contexto())).isInstanceOf(ResultadoLogin.Iniciada.class);
        assertThat(cuentas.leer(usuario).orElseThrow().intentosFallidos()).isZero();assertThat(contar("huella_fallo")).isZero();
        assertThat(contar("sesion_familia")).isEqualTo(1);
    }
    @Test void falloSmtpYReintentoNoDeshacenBloqueoNiDuplicanToken(){
        fallos(5,0);reloj.ahora=Instant.now().plusSeconds(5);
        assertThat(mensajeria.procesarCorreos()).isZero();var fila=correos("correo.cuenta_bloqueada").getFirst();
        assertThat(fila.get("intentos")).isEqualTo(1);
        jdbc.update("update outbox set proximo_intento_en=? where id=?",OffsetDateTime.ofInstant(reloj.instant().minusSeconds(1),ZoneOffset.UTC),fila.get("id"));
        assertThat(mensajeria.procesarCorreos()).isZero();assertThat(correos("correo.cuenta_bloqueada").getFirst().get("intentos")).isEqualTo(2);
        assertThat(contar("bloqueo")).isEqualTo(1);assertThat(contar("token_un_uso")).isEqualTo(1);
        assertThat(auditorias("CUENTA_BLOQUEADA")).isEqualTo(1);assertThat(eventos("usuario.bloqueado")).isEqualTo(1);
    }
}
