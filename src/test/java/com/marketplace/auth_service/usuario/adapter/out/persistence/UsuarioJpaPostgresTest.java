package com.marketplace.auth_service.usuario.adapter.out.persistence;

import com.marketplace.auth_service.AuthServiceApplication;
import com.marketplace.auth_service.configuration.UsuarioConfiguration;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioEstadoEfectivoRepository;
import com.marketplace.auth_service.usuario.application.dto.*;
import com.marketplace.auth_service.usuario.application.port.in.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.marketplace.auth_service.usuario.application.dto.CampoBasico.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate"}, showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=AuthServiceApplication.class)
@Import({UsuarioConfiguration.class, UsuarioJpaPostgresTest.Propia.class})
@Testcontainers
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class UsuarioJpaPostgresTest {
    @TestConfiguration(proxyBeanMethods=false)
    @EntityScan("com.marketplace.auth_service.usuario.adapter.out.persistence.entity")
    @EnableJpaRepositories("com.marketplace.auth_service.usuario.adapter.out.persistence.repository")
    static class Propia { }
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void base(DynamicPropertyRegistry r) {
        PG.start();
        try (var c=DriverManager.getConnection(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword());
             var in=new ClassPathResource("mfa/base-observada/schema.sql").getInputStream();
             var s=c.createStatement()) {
            s.execute(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        } catch(Exception e) { throw new IllegalStateException(e); }
        r.add("spring.datasource.url",PG::getJdbcUrl);
        r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);
    }
    @Autowired CuentasPort cuentas;
    @Autowired CuentaEscrituraInterna escritura;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired UsuarioEstadoEfectivoRepository vista;
    @MockitoBean AutorizacionPort autorizacion;

    @BeforeEach void limpiar() { jdbc.execute("truncate usuario cascade"); }

    UUID usuario(String correo,String estado,int orden) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into usuario(id,correo,nombres,apellidos,estado,creado_en) values (?,?,?,?,?::estado_cuenta,?)",
                id,correo,"Nombre","Apellido",estado,OffsetDateTime.parse("2026-01-01T00:00:00Z").plusSeconds(orden));
        return id;
    }
    void bloqueo(UUID id,String tipo,String vencimiento) {
        String motivo=tipo.equals("MANUAL")?"fixture":null;
        jdbc.update("insert into bloqueo(usuario_id,tipo,bloqueado_hasta,motivo) values (?,?::tipo_bloqueo,?::timestamptz,?)",
                id,tipo,vencimiento,motivo);
    }
    TransactionTemplate tx() { return new TransactionTemplate(tm); }
    CambioBasicos sinCambios() { return new CambioBasicos(omitido(),omitido(),omitido()); }

    @Test void leeExistenteAusenteYCorreoConEstadoEfectivo() {
        UUID id=usuario("uno@ejemplo.test","ACTIVO",1);
        bloqueo(id,"AUTOMATICO",null);
        jdbc.update("update usuario set correo_verificado=true,celular_verificado=true,mfa_habilitado=true,"
                +"intentos_fallidos_consecutivos=4,bloqueos_seguidos=2 where id=?",id);
        var s=cuentas.leer(id).orElseThrow();
        assertThat(s.estadoBase()).isEqualTo("ACTIVO");
        assertThat(s.estadoEfectivo()).isEqualTo("BLOQUEADO");
        assertThat(s.correoVerificado()).isTrue();
        assertThat(s.celularVerificado()).isTrue();
        assertThat(s.mfaHabilitado()).isTrue();
        assertThat(s.intentosFallidosConsecutivos()).isEqualTo(4);
        assertThat(s.bloqueosSeguidos()).isEqualTo(2);
        assertThat(cuentas.buscarPorCorreoNormalizado("uno@ejemplo.test")).contains(s);
        assertThat(cuentas.leer(UUID.randomUUID())).isEmpty();
        assertThat(cuentas.buscarPorCorreoNormalizado("ausente@ejemplo.test")).isEmpty();
    }

    @ParameterizedTest @CsvSource({
        "ACTIVO,AUTOMATICO,2999-01-01T00:00:00Z,BLOQUEADO",
        "ACTIVO,AUTOMATICO,2000-01-01T00:00:00Z,ACTIVO",
        "ACTIVO,AUTOMATICO,,BLOQUEADO",
        "ACTIVO,MANUAL,,BLOQUEADO",
        "INACTIVO,AUTOMATICO,,INACTIVO",
        "PENDIENTE_VERIFICACION,AUTOMATICO,,PENDIENTE_VERIFICACION"
    })
    void conservaCicloDeVidaYVigencia(String base,String tipo,String hasta,String esperado) {
        UUID id=usuario("estado@ejemplo.test",base,1);
        bloqueo(id,tipo,hasta);
        assertThat(cuentas.leer(id).orElseThrow().estadoEfectivo()).isEqualTo(esperado);
        var filtrados=cuentas.consultar(new FiltroCuenta(esperado,null,null),new Paginacion(1,20));
        assertThat(filtrados.elementos()).extracting(CuentaSnapshot::id).containsExactly(id);
        assertThat(filtrados.total()).isEqualTo(1);
    }

    @Test void cuentaSoloEfectivamenteActivos() {
        UUID activo=usuario("a@ejemplo.test","ACTIVO",1);
        UUID vencido=usuario("b@ejemplo.test","ACTIVO",2);
        UUID bloqueado=usuario("c@ejemplo.test","ACTIVO",3);
        UUID inactivo=usuario("d@ejemplo.test","INACTIVO",4);
        UUID pendiente=usuario("e@ejemplo.test","PENDIENTE_VERIFICACION",5);
        bloqueo(vencido,"AUTOMATICO","2000-01-01T00:00:00Z");
        bloqueo(bloqueado,"AUTOMATICO",null);
        assertThat(cuentas.contarActivos(Set.of(activo,vencido,bloqueado,inactivo,pendiente))).isEqualTo(2);
        assertThat(cuentas.contarActivos(Set.of())).isZero();
    }

    @Test void filtrosCombinadosAntesDelTotalYPaginacionConPuenteDeRol() {
        var ids=new HashSet<UUID>();
        for(int i=0;i<8;i++) ids.add(usuario("seleccion"+i+"@ejemplo.test","ACTIVO",i));
        usuario("seleccion-ajeno@ejemplo.test","ACTIVO",20);
        usuario("otro@ejemplo.test","ACTIVO",21);
        UUID b=usuario("seleccion-bloqueado@ejemplo.test","ACTIVO",22);
        bloqueo(b,"AUTOMATICO",null); ids.add(b);
        when(autorizacion.usuariosConRol(Rol.CLIENTE)).thenReturn(ids);
        var filtro=new FiltroCuenta("ACTIVO","CLIENTE","SELECCION");
        var p1=cuentas.consultar(filtro,new Paginacion(1,3));
        var p2=cuentas.consultar(filtro,new Paginacion(2,3));
        assertThat(p1.total()).isEqualTo(8);
        assertThat(p2.total()).isEqualTo(8);
        assertThat(p1.elementos()).extracting(CuentaSnapshot::correo)
                .containsExactly("seleccion7@ejemplo.test","seleccion6@ejemplo.test","seleccion5@ejemplo.test");
        assertThat(p2.elementos()).extracting(CuentaSnapshot::correo)
                .containsExactly("seleccion4@ejemplo.test","seleccion3@ejemplo.test","seleccion2@ejemplo.test");
        verify(autorizacion,times(2)).usuariosConRol(Rol.CLIENTE);
    }

    @Test void rolSinCuentasNoSeConvierteEnFiltroAusente() {
        usuario("cualquiera@ejemplo.test","ACTIVO",1);
        when(autorizacion.usuariosConRol(Rol.VENDEDOR)).thenReturn(Set.of());
        assertThat(cuentas.consultar(new FiltroCuenta(null,"VENDEDOR",null),Paginacion.porDefecto()).total()).isZero();
    }

    @Test void sinFiltrosSinCoincidenciasFueraDePaginaYComodinesLiterales() {
        usuario("uno_100%@ejemplo.test","ACTIVO",1);
        usuario("otro@ejemplo.test","ACTIVO",2);
        assertThat(cuentas.consultar(null,Paginacion.porDefecto()).total()).isEqualTo(2);
        assertThat(cuentas.consultar(new FiltroCuenta(null,null,"_100%"),Paginacion.porDefecto()).elementos()).hasSize(1);
        assertThat(cuentas.consultar(new FiltroCuenta(null,null,"ausente"),Paginacion.porDefecto()).total()).isZero();
        assertThat(cuentas.consultar(null,new Paginacion(3,1)).elementos()).isEmpty();
        var remota=cuentas.consultar(null,new Paginacion(Integer.MAX_VALUE,100));
        assertThat(remota.elementos()).isEmpty();
        assertThat(remota.total()).isEqualTo(2);
        verifyNoInteractions(autorizacion);
    }

    @Test void bloqueoYMutacionesSinTransaccionSeRechazanPorProxy() {
        UUID id=usuario("tx@ejemplo.test","ACTIVO",1);
        assertThatThrownBy(()->cuentas.bloquear(id)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(()->escritura.actualizarBasicos(id,sinCambios())).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(()->escritura.guardarContadores(id,new ContadoresProteccion(1,1))).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(()->escritura.guardarMfaVoluntario(id,true)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test void basicosDistinguenOmitidoNullYValorSinTocarContadoresOEstado() {
        UUID id=usuario("basicos@ejemplo.test","ACTIVO",1);
        jdbc.update("update usuario set celular='+51987654321',celular_verificado=true,mfa_habilitado=true,"
                +"intentos_fallidos_consecutivos=4,bloqueos_seguidos=3 where id=?",id);
        tx().executeWithoutResult(s->{
            cuentas.bloquear(id);
            escritura.actualizarBasicos(id,new CambioBasicos(presente("Nuevo"),omitido(),omitido()));
        });
        var inicial=cuentas.leer(id).orElseThrow();
        assertThat(inicial.nombres()).isEqualTo("Nuevo");
        assertThat(inicial.celular()).isEqualTo("+51987654321");
        assertThat(inicial.celularVerificado()).isTrue();
        tx().executeWithoutResult(s->{ cuentas.bloquear(id);
            escritura.actualizarBasicos(id,new CambioBasicos(omitido(),omitido(),presente(null))); });
        var cambio=cuentas.leer(id).orElseThrow();
        assertThat(cambio.celular()).isNull();
        assertThat(cambio.celularVerificado()).isFalse();
        assertThat(cambio.intentosFallidosConsecutivos()).isEqualTo(4);
        assertThat(cambio.bloqueosSeguidos()).isEqualTo(3);
        assertThat(cambio.estadoBase()).isEqualTo("ACTIVO");
        assertThat(cambio.mfaHabilitado()).isTrue();
    }

    @Test void guardarContadoresConservaBasicosYRevisionDeEstado() {
        UUID id=usuario("contadores@ejemplo.test","ACTIVO",1);
        bloqueo(id,"AUTOMATICO",null);
        tx().executeWithoutResult(s->{ cuentas.bloquear(id); escritura.guardarContadores(id,new ContadoresProteccion(3,4)); });
        var s=cuentas.leer(id).orElseThrow();
        assertThat(s.correo()).isEqualTo("contadores@ejemplo.test");
        assertThat(s.nombres()).isEqualTo("Nombre");
        assertThat(s.estadoEfectivo()).isEqualTo("BLOQUEADO");
        assertThat(s.bloqueosSeguidos()).isEqualTo(3);
        assertThat(s.intentosFallidosConsecutivos()).isEqualTo(4);
    }

    @Test void rollbackRevierteBasicosYContadoresEnElCommitDelCoordinador() {
        UUID id=usuario("rollback@ejemplo.test","ACTIVO",1);
        assertThatThrownBy(()->tx().executeWithoutResult(s->{
            cuentas.bloquear(id);
            escritura.actualizarBasicos(id,new CambioBasicos(presente("No guardar"),omitido(),omitido()));
            escritura.guardarContadores(id,new ContadoresProteccion(2,4));
            throw new IllegalStateException("fallo posterior del coordinador");
        })).isInstanceOf(IllegalStateException.class);
        var despues=cuentas.leer(id).orElseThrow();
        assertThat(despues.nombres()).isEqualTo("Nombre");
        assertThat(despues.bloqueosSeguidos()).isZero();
        assertThat(despues.intentosFallidosConsecutivos()).isZero();
    }

    @Test void dosCoordinadoresSeSerializanHastaElCommitExterior() throws Exception {
        UUID id=usuario("concurrencia@ejemplo.test","ACTIVO",1);
        var tomado=new CountDownLatch(1); var liberar=new CountDownLatch(1); var intento=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var primero=pool.submit(()->tx().executeWithoutResult(s->{
                cuentas.bloquear(id); tomado.countDown();
                try { if(!liberar.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout fixture"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                escritura.guardarContadores(id,new ContadoresProteccion(1,1));
            }));
            assertThat(tomado.await(10,TimeUnit.SECONDS)).isTrue();
            var segundo=pool.submit(()->tx().executeWithoutResult(s->{
                intento.countDown(); var actual=cuentas.bloquear(id);
                escritura.guardarContadores(id,new ContadoresProteccion(actual.bloqueosSeguidos()+1,
                        actual.intentosFallidosConsecutivos()+1));
            }));
            try {
                assertThat(intento.await(10,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(()->segundo.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { liberar.countDown(); }
            primero.get(10,TimeUnit.SECONDS); segundo.get(10,TimeUnit.SECONDS);
        }
        var s=cuentas.leer(id).orElseThrow();
        assertThat(s.bloqueosSeguidos()).isEqualTo(2);
        assertThat(s.intentosFallidosConsecutivos()).isEqualTo(2);
    }

    @Test void vistaNoExponeEscriturasYMfaSigueReservado() {
        assertThat(Arrays.stream(UsuarioEstadoEfectivoRepository.class.getMethods()).map(java.lang.reflect.Method::getName))
                .noneMatch(n->n.startsWith("save")||n.startsWith("delete"));
        UUID id=usuario("mfa@ejemplo.test","ACTIVO",1);
        assertThatThrownBy(()->tx().executeWithoutResult(s->escritura.guardarMfaVoluntario(id,true)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
