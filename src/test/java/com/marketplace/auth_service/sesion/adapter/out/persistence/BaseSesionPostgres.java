package com.marketplace.auth_service.sesion.adapter.out.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/** Scripts completos: no separar los bloques DO de PostgreSQL por punto y coma. */
final class BaseSesionPostgres {
    private BaseSesionPostgres() { }

    static void cargarEsquema(Connection conexion) throws SQLException, IOException {
        ejecutar(conexion, new ClassPathResource("sesion/base-observada/schema.sql"));
    }

    static void cargarHistoricos(Connection conexion, String archivo) throws SQLException, IOException {
        ejecutar(conexion, new ClassPathResource("sesion/" + archivo));
    }

    static void migrar(Connection conexion) throws SQLException, IOException {
        if (!conexion.getAutoCommit()) {
            throw new IllegalStateException("La prueba de migración requiere su propia transacción");
        }
        conexion.setAutoCommit(false);
        try {
            ejecutar(conexion, new FileSystemResource("docs/migraciones-propuestas/V102__sesion_familia_acceso.sql"));
            conexion.commit();
        } catch (SQLException | IOException fallo) {
            conexion.rollback();
            throw fallo;
        } finally {
            conexion.setAutoCommit(true);
        }
    }

    private static void ejecutar(Connection conexion, Resource recurso) throws SQLException, IOException {
        try (var entrada = recurso.getInputStream(); var sentencia = conexion.createStatement()) {
            sentencia.execute(new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
