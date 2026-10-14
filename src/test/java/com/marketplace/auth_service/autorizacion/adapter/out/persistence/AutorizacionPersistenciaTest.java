package com.marketplace.auth_service.autorizacion.adapter.out.persistence;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.RolJpaEntity;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.UsuarioRolJpaEntity;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.CatalogoJpaRepository;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AsociacionesRolPort;
import com.marketplace.auth_service.autorizacion.domain.model.CatalogoAutorizacion;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.configuration.AutorizacionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mappings, catálogo sembrado y cálculo de permisos sobre PostgreSQL real
 * (EP-05-T001). Cada prueba corre en una transacción que se revierte al final.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AutorizacionConfig.class)
@RequierePostgres
class AutorizacionPersistenciaTest extends PostgresDePrueba {

    @Autowired
    private AutorizacionPort autorizacion;
    @Autowired
    private AsociacionesRolPort asociaciones;
    @Autowired
    private UsuarioRolJpaRepository usuarioRol;
    @Autowired
    private CatalogoJpaRepository catalogo;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private UUID personaCon(Rol... roles) {
        UUID id = UUID.randomUUID();
        for (Rol rol : roles) {
            usuarioRol.save(new UsuarioRolJpaEntity(id, rol));
        }
        usuarioRol.flush();
        return id;
    }

    // ── Catálogo ──────────────────────────────────────────────

    @Test
    void elCatalogoTieneLosSeisRolesConSusEtiquetasCanonicas() {
        Map<Rol, String> nombres = catalogo.findAll().stream()
                .collect(Collectors.toMap(RolJpaEntity::getCodigo, RolJpaEntity::getNombre));

        assertThat(nombres).hasSize(6);
        for (Rol rol : Rol.values()) {
            assertThat(nombres.get(rol)).isEqualTo(rol.etiqueta());
        }
    }

    @Test
    void laMatrizDeLaBaseEsExactamenteLaDelCatalogo() {
        Map<Rol, Set<String>> esperada = CatalogoAutorizacion.matriz().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().map(Permiso::codigo).collect(Collectors.toSet())));

        assertThat(asociaciones.matrizPersistida()).isEqualTo(esperada);
    }

    @Test
    void soloHayPermisosDeSeguridadConPunto() {
        assertThat(jdbc().queryForList("SELECT DISTINCT modulo FROM permiso", String.class))
                .containsExactly("seguridad");
        assertThat(jdbc().queryForList("SELECT codigo FROM permiso", String.class))
                .hasSize(10)
                .allSatisfy(c -> assertThat(Permiso.desdeCodigo(c)).isPresent());
    }

    @Test
    void laSemillaEsIdempotenteYCorrigeLasEtiquetasDeV100() {
        jdbc().update("UPDATE rol SET nombre = 'Cliente Final' WHERE codigo = 'CLIENTE'");

        // La conexión de la transacción de la prueba: ve el UPDATE anterior y se revierte con ella.
        Connection conexion = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/autorizacion/R__catalogo_autorizacion.sql"));
        } finally {
            DataSourceUtils.releaseConnection(conexion, dataSource);
        }

        assertThat(jdbc().queryForObject("SELECT nombre FROM rol WHERE codigo = 'CLIENTE'", String.class))
                .isEqualTo("Cliente");
        assertThat(jdbc().queryForObject("SELECT count(*) FROM rol_permiso", Integer.class)).isEqualTo(10);
    }

    // ── Asociaciones ──────────────────────────────────────────

    @Test
    void unaPersonaPuedeTenerVariosRoles() {
        UUID id = personaCon(Rol.CLIENTE, Rol.GESTOR_DESPACHO);

        assertThat(asociaciones.rolesDe(id)).containsExactlyInAnyOrder(Rol.CLIENTE, Rol.GESTOR_DESPACHO);
    }

    @Test
    void elMismoRolNoSePuedeAsociarDosVeces() {
        UUID id = personaCon(Rol.VENDEDOR);

        assertThatThrownBy(() -> jdbc().update(
                "INSERT INTO usuario_rol (usuario_id, rol_codigo) VALUES (?, 'VENDEDOR')", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void laBaseRechazaCodigosDeRolFueraDelCatalogo() {
        assertThatThrownBy(() -> jdbc().update(
                "INSERT INTO usuario_rol (usuario_id, rol_codigo) VALUES (?, 'SUPER_ADMIN')", UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    // ── Permisos efectivos (RF-11.4) ──────────────────────────

    @Test
    void sinRolesLasListasSonVacias() {
        PermisosResueltos efectivos = autorizacion.resolver(UUID.randomUUID());

        assertThat(efectivos.codigosRoles()).isEmpty();
        assertThat(efectivos.codigosPermisos()).isEmpty();
    }

    @Test
    void esc11_4_adminYVendedorSumanDiezPermisosSinDuplicados() {
        PermisosResueltos efectivos = autorizacion.resolver(personaCon(Rol.ADMIN_SISTEMA, Rol.VENDEDOR));

        assertThat(efectivos.codigosRoles()).containsExactly("ADMIN_SISTEMA", "VENDEDOR");
        assertThat(efectivos.codigosPermisos()).hasSize(10).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(
                        Set.of(Permiso.values()).stream().map(Permiso::codigo).toList());
    }

    @Test
    void esc11_7_unAdministradorTieneLosOchoPermisosDeAdministracion() {
        PermisosResueltos efectivos = autorizacion.resolver(personaCon(Rol.ADMIN_SISTEMA));

        assertThat(efectivos.codigosPermisos()).hasSize(8)
                .doesNotContain("cliente.registrar", "cliente.buscar")
                .allSatisfy(p -> assertThat(p).contains("."));
    }

    @Test
    void losRolesDeGestionDeOtrosModulosNoAportanPermisosDeSeguridad() {
        PermisosResueltos efectivos = autorizacion.resolver(
                personaCon(Rol.ADMIN_VENTAS, Rol.GESTOR_DESPACHO, Rol.GESTOR_COMERCIAL, Rol.CLIENTE));

        assertThat(efectivos.codigosRoles()).hasSize(4);
        assertThat(efectivos.codigosPermisos()).isEmpty();
    }

    @Test
    void unPermisoAjenoEnLaBaseNuncaLlegaAlToken() {
        jdbc().update("INSERT INTO permiso (codigo, modulo) VALUES ('pedido.crear', 'ventas')");
        jdbc().update("INSERT INTO rol_permiso (rol_codigo, permiso_codigo) VALUES ('CLIENTE', 'pedido.crear')");
        UUID cliente = personaCon(Rol.CLIENTE);

        assertThatThrownBy(() -> autorizacion.resolver(cliente))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pedido.crear");
    }

    @Test
    void elCalculoDePermisosTardaMenosDe15Ms() {
        UUID id = personaCon(Rol.ADMIN_SISTEMA, Rol.VENDEDOR);
        autorizacion.resolver(id); // calentamiento

        int vueltas = 50;
        long inicio = System.nanoTime();
        for (int i = 0; i < vueltas; i++) {
            autorizacion.resolver(id);
        }
        double promedioMs = (System.nanoTime() - inicio) / 1_000_000.0 / vueltas;

        System.out.printf("MEDICION resolver() promedio=%.2f ms en %d llamadas%n", promedioMs, vueltas);
        assertThat(promedioMs).isLessThan(15.0);
    }
}
