package com.marketplace.auth_service.autorizacion.adapter.out.persistence;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Locale;
import java.util.UUID;

/**
 * Base de las pruebas de persistencia de autorización: PostgreSQL real, nunca H2
 * (los enum, CHECK y locks de PostgreSQL no se pueden simular).
 *
 * <ul>
 *   <li>Si existe {@code PRUEBAS_POSTGRES_URL} (p. ej. {@code jdbc:postgresql://localhost:5432/postgres}),
 *       usa esa base, con {@code PRUEBAS_POSTGRES_USUARIO}/{@code PRUEBAS_POSTGRES_CONTRASENA}.
 *       Sirve en equipos sin Docker. <b>Nunca apuntarla a Supabase.</b></li>
 *   <li>Si no, arranca PostgreSQL 16.6 con Testcontainers cuando hay Docker.</li>
 *   <li>Sin ninguno de los dos, las pruebas se omiten (aparecen como omitidas, no como pasadas):
 *       cada clase concreta lleva {@link RequierePostgres}.</li>
 * </ul>
 *
 * <p>Cada clase usa un esquema nuevo con nombre aleatorio, así nunca toca datos
 * existentes. Flyway crea en él el esquema de prueba de V1 y aplica la semilla
 * real del catálogo; Hibernate valida los mappings ({@code ddl-auto=validate}).
 */
public abstract class PostgresDePrueba {

    private static final String URL_EXTERNA = System.getenv("PRUEBAS_POSTGRES_URL");
    private static PostgreSQLContainer<?> contenedor;

    public static boolean hayPostgres() {
        return URL_EXTERNA != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registro) {
        String esquema = "prueba_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        String url;
        String usuario;
        String contrasena;
        if (URL_EXTERNA != null) {
            url = URL_EXTERNA;
            usuario = System.getenv().getOrDefault("PRUEBAS_POSTGRES_USUARIO", "postgres");
            contrasena = System.getenv().getOrDefault("PRUEBAS_POSTGRES_CONTRASENA", "postgres");
        } else {
            iniciarContenedor();
            url = contenedor.getJdbcUrl();
            usuario = contenedor.getUsername();
            contrasena = contenedor.getPassword();
        }
        String conEsquema = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + esquema;

        registro.add("spring.datasource.url", () -> conEsquema);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
        registro.add("spring.flyway.enabled", () -> "true");
        registro.add("spring.flyway.schemas", () -> esquema);
        registro.add("spring.flyway.default-schema", () -> esquema);
        registro.add("spring.flyway.locations", () -> "classpath:db/prueba-autorizacion,classpath:db/autorizacion");
        registro.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registro.add("spring.jpa.open-in-view", () -> "false");
    }

    private static synchronized void iniciarContenedor() {
        if (contenedor == null) {
            contenedor = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.6"));
            contenedor.start();
        }
    }
}
