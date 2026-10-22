package com.marketplace.auth_service.perfil;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base desechable para las pruebas de EP-08-T007: esquema observado + V106 (registro_id de auditoría y
 * columnas de reclamo del outbox, ya aprobadas) + semilla real del catálogo de autorización.
 * Usa {@code PRUEBAS_POSTGRES_URL} (nunca Supabase) o Docker; sin ninguno devuelve {@code false}.
 */
final class BasePerfilPostgres {
    private static PostgreSQLContainer<?> contenedor;

    private BasePerfilPostgres() { }

    /** @return {@code true} si registró una base; {@code false} si no hay PostgreSQL (la clase se omite). */
    static boolean registrar(DynamicPropertyRegistry registro, String prefijo) throws Exception {
        String url = System.getenv("PRUEBAS_POSTGRES_URL");
        String usuario;
        String contrasena;
        if (url != null) {
            usuario = System.getenv().getOrDefault("PRUEBAS_POSTGRES_USUARIO", "postgres");
            contrasena = System.getenv().getOrDefault("PRUEBAS_POSTGRES_CONTRASENA", "postgres");
        } else if (DockerClientFactory.instance().isDockerAvailable()) {
            synchronized (BasePerfilPostgres.class) {
                if (contenedor == null) {
                    contenedor = new PostgreSQLContainer<>("postgres:17.6");
                    contenedor.start();
                }
            }
            url = contenedor.getJdbcUrl();
            usuario = contenedor.getUsername();
            contrasena = contenedor.getPassword();
        } else {
            return false;
        }
        String nombre = prefijo + "_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        try (var admin = DriverManager.getConnection(url, usuario, contrasena); var s = admin.createStatement()) {
            s.execute("CREATE DATABASE " + nombre);
        }
        String propia = url.replaceFirst("/[^/?]+(\\?|$)", "/" + nombre + "$1");
        try (var c = DriverManager.getConnection(propia, usuario, contrasena); var s = c.createStatement()) {
            s.execute(leer("mfa/base-observada/schema.sql"));
            s.execute(leer("db/migration/V106__auditoria_registro_id_y_outbox_reintento.sql"));
            s.execute(leer("db/autorizacion/R__catalogo_autorizacion.sql"));
        }
        registro.add("spring.datasource.url", () -> propia);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
        return true;
    }

    private static String leer(String recurso) throws Exception {
        try (var in = new ClassPathResource(recurso).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
