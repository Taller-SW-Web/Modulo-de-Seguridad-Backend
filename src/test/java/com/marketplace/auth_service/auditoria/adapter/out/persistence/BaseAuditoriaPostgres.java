package com.marketplace.auth_service.auditoria.adapter.out.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/** Esquema observado pre-V106 + migración propuesta; sin UPDATE por punto y coma en DO. */
final class BaseAuditoriaPostgres {
    private BaseAuditoriaPostgres() { }

    static void cargarEsquema(Connection conexion) throws SQLException, IOException {
        ejecutar(conexion, new ClassPathResource("auditoria/base-observada/schema.sql"));
    }

    static void migrar(Connection conexion) throws SQLException, IOException {
        if (!conexion.getAutoCommit()) {
            throw new IllegalStateException("La prueba de migración requiere su propia transacción");
        }
        conexion.setAutoCommit(false);
        try {
            ejecutar(conexion, new FileSystemResource(
                    "docs/migraciones-propuestas/V106__auditoria_registro_id_y_outbox_reintento.sql"));
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
