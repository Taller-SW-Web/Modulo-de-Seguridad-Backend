package com.marketplace.auth_service.sesion.adapter.out.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cada caso usa una base desechable nueva y la propuesta SQL completa. */
@Testcontainers
class MigracionV102PostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    private Connection conexion;

    @BeforeEach
    void prepararBaseAislada() throws Exception {
        String nombre = "sesion_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var sentencia = admin.createStatement()) {
            sentencia.execute("create database " + nombre);
        }
        conexion = DriverManager.getConnection("jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/" + nombre, POSTGRES.getUsername(), POSTGRES.getPassword());
        BaseSesionPostgres.cargarEsquema(conexion);
    }

    @AfterEach
    void cerrarBase() throws SQLException {
        if (conexion != null) conexion.close();
    }

    @Test
    void aplicaSinHistoricosYNoFabricaAsociacionesDeAccesos() throws Exception {
        BaseSesionPostgres.migrar(conexion);
        assertThat(numero("select count(*) from sesion_familia")).isZero();
        assertThat(numero("select count(*) from acceso_sesion")).isZero();
        assertThat(numero("select count(*) from token_refresco")).isZero();
    }

    @Test
    void backfillConservaUuidUsuarioFechaHashesYRevocaciones() throws Exception {
        BaseSesionPostgres.cargarHistoricos(conexion, "historicos-coherentes-v102.sql");
        BaseSesionPostgres.migrar(conexion);
        assertThat(numero("select count(*) from sesion_familia")).isEqualTo(2);
        assertThat(numero("select count(*) from token_refresco")).isEqualTo(3);
        assertThat(numero("select count(*) from acceso_sesion")).isZero();
        assertThat(numero("""
                select count(*) from sesion_familia
                where id='00000000-0000-0000-0000-0000000000aa'
                  and usuario_id='00000000-0000-0000-0000-0000000000a1'
                  and creado_en='2026-10-12 20:00:00+00' and revocada and revocada_en is not null
                """)).isEqualTo(1);
        assertThat(numero("select count(*) from token_refresco where hash_token=repeat('a',64) and revocado and usado")).isEqualTo(1);
        assertThat(numero("select count(*) from token_refresco where hash_token=repeat('b',64) and not revocado and not usado")).isEqualTo(1);
        assertThat(numero("select count(*) from sesion_familia where not revocada and revocada_en is null")).isEqualTo(1);
    }

    @Test
    void familiaConDosUsuariosAbortaSinObjetosNiCambiosParciales() throws Exception {
        BaseSesionPostgres.cargarHistoricos(conexion, "historicos-incoherentes-v102.sql");
        assertThatThrownBy(() -> BaseSesionPostgres.migrar(conexion))
                .isInstanceOf(SQLException.class).hasMessageContaining("más de un usuario");
        comprobarSinV102();
        assertThat(numero("select count(*) from token_refresco")).isEqualTo(2);
    }

    @Test
    void errorDuranteBackfillRevierteTambienDdlYConservaHistoricos() throws Exception {
        BaseSesionPostgres.cargarHistoricos(conexion, "historicos-coherentes-v102.sql");
        ejecutar("""
                update token_refresco set expiracion='2030-01-08 00:00:00+00', creado_en='2030-01-01 00:00:00+00'
                where familia_id='00000000-0000-0000-0000-0000000000aa'
                """);
        assertThatThrownBy(() -> BaseSesionPostgres.migrar(conexion))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_sesion_familia_revocada");
        comprobarSinV102();
        assertThat(numero("select count(*) from token_refresco where creado_en='2030-01-01 00:00:00+00'")).isEqualTo(2);
    }

    @Test
    void instalaIndicesYValidaAmbasFkCompuestas() throws Exception {
        BaseSesionPostgres.migrar(conexion);
        assertThat(numero("""
                select count(*) from pg_indexes where schemaname='public' and indexname in
                ('idx_sesion_familia_usuario','idx_acceso_sesion_familia','idx_acceso_sesion_expira')
                """)).isEqualTo(3);
        assertThat(numero("""
                select count(*) from pg_constraint where conname in
                ('fk_token_refresco_familia','fk_acceso_sesion_familia') and convalidated
                  and array_length(conkey,1)=2
                """)).isEqualTo(2);
        assertThat(numero("select count(*) from pg_class where relname='uq_sesion_familia_id_usuario'")).isEqualTo(1);
    }

    @Test
    void activaRlsYRevocaPrivilegiosHeredadosPublicos() throws Exception {
        // Simula defaults de un esquema expuesto antes de ejecutar V102.
        ejecutar("alter default privileges grant select, insert, update, delete on tables to public");
        BaseSesionPostgres.cargarHistoricos(conexion, "historicos-coherentes-v102.sql");
        BaseSesionPostgres.migrar(conexion);
        assertThat(numero("""
                select count(*) from pg_class where relname in ('sesion_familia','acceso_sesion') and relrowsecurity
                """)).isEqualTo(2);
        assertThat(numero("""
                select count(*) from pg_class c cross join lateral aclexplode(c.relacl) a
                where c.relname in ('sesion_familia','acceso_sesion') and a.grantee=0
                """)).isZero();

        String rol = "sesion_lector_" + UUID.randomUUID().toString().replace("-", "");
        ejecutar("create role " + rol + " nologin");
        ejecutar("grant usage on schema public to " + rol);
        ejecutar("grant select on sesion_familia, acceso_sesion to " + rol);
        try {
            ejecutar("set role " + rol);
            assertThat(numero("select count(*) from sesion_familia")).as("Sin política del runtime, RLS deniega filas").isZero();
        } finally {
            ejecutar("reset role");
        }
    }

    @Test
    void revocaAnonYAuthenticatedSiExistenSinCrearEsosRoles() throws Exception {
        String[] roles = {"anon", "authenticated"};
        for (String rol : roles) {
            if (numero("select count(*) from pg_roles where rolname='" + rol + "'") == 0) {
                ejecutar("create role " + rol + " nologin");
            }
            ejecutar("alter default privileges grant select, insert, update, delete on tables to " + rol);
        }
        BaseSesionPostgres.migrar(conexion);
        for (String rol : roles) {
            assertThat(numero("""
                    select count(*) from pg_class c cross join lateral aclexplode(c.relacl) a
                    join pg_roles r on r.oid=a.grantee
                    where c.relname in ('sesion_familia','acceso_sesion') and r.rolname='%s'
                    """.formatted(rol))).isZero();
        }
    }

    @Test
    void familiaQueSoloTieneTokensUsadosOVencidosNoSeEliminaNiSeRevocaPorInferencia() throws Exception {
        BaseSesionPostgres.cargarHistoricos(conexion, "historicos-coherentes-v102.sql");
        ejecutar("""
                update token_refresco set usado=true, creado_en='2020-01-01 00:00:00+00', expiracion='2020-01-08 00:00:00+00'
                where familia_id='00000000-0000-0000-0000-0000000000bb'
                """);
        BaseSesionPostgres.migrar(conexion);
        assertThat(numero("""
                select count(*) from sesion_familia where id='00000000-0000-0000-0000-0000000000bb'
                  and creado_en='2020-01-01 00:00:00+00' and not revocada
                """)).isEqualTo(1);
        assertThat(numero("select count(*) from token_refresco")).isEqualTo(3);
    }

    @Test
    void borrarFamiliaEliminaSoloSusAccesosYRefrescos() throws Exception {
        BaseSesionPostgres.cargarHistoricos(conexion, "historicos-coherentes-v102.sql");
        BaseSesionPostgres.migrar(conexion);
        crearAccesosParaFamilias();
        ejecutar("delete from sesion_familia where id='00000000-0000-0000-0000-0000000000aa'");
        assertThat(numero("select count(*) from acceso_sesion")).isEqualTo(1);
        assertThat(numero("select count(*) from token_refresco")).isEqualTo(1);
        assertThat(numero("select count(*) from sesion_familia")).isEqualTo(1);
        assertThat(numero("select count(*) from usuario")).isEqualTo(2);
    }

    @Test
    void borrarUsuarioRecorreTodaLaCadenaSinHuerfanosYPermiteRollback() throws Exception {
        BaseSesionPostgres.cargarHistoricos(conexion, "historicos-coherentes-v102.sql");
        BaseSesionPostgres.migrar(conexion);
        crearAccesosParaFamilias();
        conexion.setAutoCommit(false);
        try {
            ejecutar("delete from usuario where id='00000000-0000-0000-0000-0000000000a1'");
            comprobarSoloSegundaCuenta();
            conexion.rollback();
        } finally { conexion.setAutoCommit(true); }
        assertThat(numero("select count(*) from usuario")).isEqualTo(2);
        assertThat(numero("select count(*) from sesion_familia")).isEqualTo(2);
        assertThat(numero("select count(*) from acceso_sesion")).isEqualTo(2);
        assertThat(numero("select count(*) from token_refresco")).isEqualTo(3);
        ejecutar("delete from usuario where id='00000000-0000-0000-0000-0000000000a1'");
        comprobarSoloSegundaCuenta();
    }

    private void crearAccesosParaFamilias() throws SQLException {
        ejecutar("""
                insert into acceso_sesion(usuario_id,familia_id,creado_en,expira_en)
                select usuario_id,id,'2026-10-15 17:00:00+00'::timestamptz,
                       '2026-10-15 17:15:00+00'::timestamptz from sesion_familia
                """);
    }
    private void comprobarSoloSegundaCuenta() throws SQLException {
        assertThat(numero("select count(*) from usuario")).isEqualTo(1);
        assertThat(numero("select count(*) from sesion_familia")).isEqualTo(1);
        assertThat(numero("select count(*) from acceso_sesion")).isEqualTo(1);
        assertThat(numero("select count(*) from token_refresco")).isEqualTo(1);
        assertThat(numero("""
                select count(*) from sesion_familia f left join usuario u on u.id=f.usuario_id where u.id is null
                """)).isZero();
        assertThat(numero("""
                select count(*) from acceso_sesion a left join sesion_familia f
                on (a.familia_id,a.usuario_id)=(f.id,f.usuario_id) where f.id is null
                """)).isZero();
        assertThat(numero("""
                select count(*) from token_refresco t left join sesion_familia f
                on (t.familia_id,t.usuario_id)=(f.id,f.usuario_id) where f.id is null
                """)).isZero();
    }

    private void comprobarSinV102() throws SQLException {
        assertThat(numero("select count(*) from pg_class where relname in ('sesion_familia','acceso_sesion')")).isZero();
        assertThat(numero("select count(*) from pg_constraint where conname='fk_token_refresco_familia'")).isZero();
    }

    private int numero(String sql) throws SQLException {
        try (var sentencia = conexion.createStatement(); var filas = sentencia.executeQuery(sql)) {
            filas.next();
            return filas.getInt(1);
        }
    }

    private void ejecutar(String sql) throws SQLException {
        try (var sentencia = conexion.createStatement()) { sentencia.execute(sql); }
    }
}
