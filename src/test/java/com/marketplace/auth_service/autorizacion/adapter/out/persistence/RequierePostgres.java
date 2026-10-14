package com.marketplace.auth_service.autorizacion.adapter.out.persistence;

import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ejecuta la clase solo si hay PostgreSQL disponible ({@code PRUEBAS_POSTGRES_URL}
 * o Docker); si no, JUnit la marca como omitida. Va en cada clase concreta
 * porque las condiciones de JUnit no se heredan de {@link PostgresDePrueba}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnabledIf(value = "com.marketplace.auth_service.autorizacion.adapter.out.persistence.PostgresDePrueba#hayPostgres",
        disabledReason = "Sin PostgreSQL: definir PRUEBAS_POSTGRES_URL o tener Docker")
public @interface RequierePostgres {
}
