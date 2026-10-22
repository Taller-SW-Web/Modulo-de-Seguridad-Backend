package com.marketplace.auth_service.credencial.adapter.out.persistence;

import com.marketplace.auth_service.credencial.application.port.out.CuotaRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.HashCorreoSolicitudPort;
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

/** Persistencia atómica de RF-08.8 en la tabla compartida solicitud_limitada. */
public class SolicitudesRecuperacionPostgresAdapter implements CuotaRecuperacionPort {
    static final int MAXIMO = 3;
    static final Duration VENTANA = Duration.ofHours(1);
    private final JdbcTemplate jdbc;
    private final HashCorreoSolicitudPort hashCorreo;

    public SolicitudesRecuperacionPostgresAdapter(JdbcTemplate jdbc, HashCorreoSolicitudPort hashCorreo) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.hashCorreo = Objects.requireNonNull(hashCorreo);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Duration reservar(String correoNormalizado, Instant ahora) {
        Objects.requireNonNull(ahora, "ahora");
        String clave = hashCorreo.generar(Objects.requireNonNull(correoNormalizado, "correoNormalizado"));
        bloquearClave(clave);
        OffsetDateTime limite = OffsetDateTime.ofInstant(ahora.minus(VENTANA), ZoneOffset.UTC);
        Long cantidad = jdbc.queryForObject("""
                select count(*) from solicitud_limitada
                where tipo = 'RECUPERACION'::tipo_limite and clave_hash = ? and creado_en > ?
                """, Long.class, clave, limite);
        if (cantidad != null && cantidad >= MAXIMO) {
            OffsetDateTime primera = jdbc.queryForObject("""
                    select min(creado_en) from solicitud_limitada
                    where tipo = 'RECUPERACION'::tipo_limite and clave_hash = ? and creado_en > ?
                    """, OffsetDateTime.class, clave, limite);
            Instant libera = primera.toInstant().plus(VENTANA);
            Duration espera = Duration.between(ahora, libera);
            return espera.isNegative() || espera.isZero() ? Duration.ofSeconds(1) : espera;
        }
        jdbc.update("""
                insert into solicitud_limitada(tipo, clave_hash, creado_en)
                values ('RECUPERACION'::tipo_limite, ?, ?)
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
