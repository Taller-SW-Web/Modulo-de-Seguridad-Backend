package com.marketplace.auth_service.sesion.adapter.out.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EP-02-T002: datos previos incoherentes detienen V102; no se elige un usuario
 * arbitrario ni se deja una raíz parcial. Contenedor desechable, sin Supabase.
 */
@Testcontainers
@RequiereSesionPostgres
class MigracionV102AbortadaPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    private static final UUID FAMILIA_INCOHERENTE = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    @BeforeAll
    static void esquemaPrevioIncoherente() throws Exception {
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("sesion/esquema-previo-v102.sql"));
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("sesion/historicos-incoherentes-v102.sql"));
        }
    }

    @Test
    void familiaConDosUsuariosAbortaSinRaizParcial() throws Exception {
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            assertThatThrownBy(() -> ScriptUtils.executeSqlScript(conexion,
                    new FileSystemResource("docs/migraciones-propuestas/V102__sesion_familia_acceso.sql")))
                    .rootCause().hasMessageContaining("más de un usuario");
            try (var consulta = conexion.createStatement();
                    var filas = consulta.executeQuery(
                            "select count(*) from sesion_familia where id='" + FAMILIA_INCOHERENTE + "'")) {
                filas.next();
                assertThat(filas.getInt(1)).isZero();
            }
        }
    }
}
