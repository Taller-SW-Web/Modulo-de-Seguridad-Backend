package com.marketplace.auth_service.sesion.adapter.out.persistence;

import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Ejecuta la clase solo si hay PostgreSQL disponible; si no, queda omitida. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnabledIf(value = "com.marketplace.auth_service.sesion.adapter.out.persistence.PostgresSesion#hayPostgres",
        disabledReason = "Sin PostgreSQL: definir PRUEBAS_POSTGRES_URL o tener Docker")
public @interface RequiereSesionPostgres {
}
