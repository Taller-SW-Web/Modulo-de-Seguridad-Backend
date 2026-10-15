package com.marketplace.auth_service.sesion.adapter.out.persistence;

import org.testcontainers.DockerClientFactory;

/** PostgreSQL real para pruebas de sesión, nunca H2 ni Supabase. */
public final class PostgresSesion {
    private PostgresSesion() { }

    public static boolean hayPostgres() {
        return System.getenv("PRUEBAS_POSTGRES_URL") != null
                || DockerClientFactory.instance().isDockerAvailable();
    }
}
