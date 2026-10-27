package com.marketplace.auth_service.usuario.adapter.out.persistence.adapter;

import com.marketplace.auth_service.usuario.application.port.out.CuotaReenvioPort;
import com.marketplace.auth_service.usuario.application.port.out.HuellaCorreoPort;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-02.4 en la tabla compartida {@code solicitud_limitada} ({@code REENVIO_VERIFICACION}), con la
 * misma huella y el mismo bloqueo por clave que la recuperación de Juan José: contar y reservar
 * van bajo {@code pg_advisory_xact_lock}, así dos peticiones simultáneas no pasan ambas la cuarta.
 */
public class CuotaReenvioPostgresAdapter implements CuotaReenvioPort {
    static final int MAXIMO = 3;
    static final Duration VENTANA = Duration.ofHours(1);
    private final JdbcTemplate jdbc;
    private final HuellaCorreoPort huellas;

    public CuotaReenvioPostgresAdapter(JdbcTemplate jdbc, HuellaCorreoPort huellas) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.huellas = Objects.requireNonNull(huellas);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Duration reservar(String correoNormalizado, Instant ahora) {
        String clave = huellas.generar(Objects.requireNonNull(correoNormalizado, "correoNormalizado"));
        bloquearClave(clave);
        OffsetDateTime desde = OffsetDateTime.ofInstant(ahora.minus(VENTANA), ZoneOffset.UTC);
        Long cantidad = jdbc.queryForObject("""
                select count(*) from solicitud_limitada
                where tipo = 'REENVIO_VERIFICACION'::tipo_limite and clave_hash = ? and creado_en > ?
                """, Long.class, clave, desde);
        if (cantidad != null && cantidad >= MAXIMO) {
            OffsetDateTime primera = jdbc.queryForObject("""
                    select min(creado_en) from solicitud_limitada
                    where tipo = 'REENVIO_VERIFICACION'::tipo_limite and clave_hash = ? and creado_en > ?
                    """, OffsetDateTime.class, clave, desde);
            Duration espera = Duration.between(ahora, primera.toInstant().plus(VENTANA));
            return espera.isNegative() || espera.isZero() ? Duration.ofSeconds(1) : espera;
        }
        jdbc.update("""
                insert into solicitud_limitada(tipo, clave_hash, creado_en)
                values ('REENVIO_VERIFICACION'::tipo_limite, ?, ?)
                """, clave, OffsetDateTime.ofInstant(ahora, ZoneOffset.UTC));
        return Duration.ZERO;
    }

    private void bloquearClave(String clave) {
        jdbc.execute((ConnectionCallback<Void>) conexion -> {
            try (PreparedStatement consulta = conexion.prepareStatement(
                    "select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                consulta.setString(1, clave);
                try (var resultado = consulta.executeQuery()) { resultado.next(); }
            }
            return null;
        });
    }
}
