package com.marketplace.auth_service.mfa.adapter.out.persistence;

import com.marketplace.auth_service.mfa.application.port.out.PersistenciaCambioMfaPort;
import com.marketplace.auth_service.mfa.domain.model.CambioMfa;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** SQL propio parametrizado; participa en el mismo DataSource/commit que OTP y cuentas. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class CambioMfaJdbcAdapter implements PersistenciaCambioMfaPort {
    private final JdbcTemplate jdbc;
    public CambioMfaJdbcAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<CambioMfa> buscarPorHash(String hash) {
        return jdbc.query("select * from cambio_mfa where hash_token = ?", this::mapear, hash).stream().findFirst();
    }
    @Override public Optional<CambioMfa> bloquear(UUID id) {
        return jdbc.query("select * from cambio_mfa where id = ? for update", this::mapear, id).stream().findFirst();
    }
    @Override public void guardar(CambioMfa c) {
        jdbc.update("""
                insert into cambio_mfa(id,usuario_id,familia_id,habilitar,hash_token,creado_en,expira_en,consumido,invalidado_en,otp_actual_id)
                values (?,?,?,?,?,?,?,?,?,?) on conflict(id) do update set
                consumido=excluded.consumido, invalidado_en=excluded.invalidado_en, otp_actual_id=excluded.otp_actual_id
                """, c.id(), c.usuarioId(), c.familiaId(), c.habilitar(), c.hashToken(), Timestamp.from(c.creadoEn()),
                Timestamp.from(c.expiraEn()), c.consumido(), c.invalidadoEn() == null ? null : Timestamp.from(c.invalidadoEn()), c.otpActualId());
    }
    @Override public void invalidarPendientes(UUID usuario, Instant ahora) {
        jdbc.update("update cambio_mfa set invalidado_en=? where usuario_id=? and not consumido and invalidado_en is null",
                Timestamp.from(ahora), usuario);
    }
    private CambioMfa mapear(ResultSet r, int fila) throws SQLException {
        Timestamp invalidado = r.getTimestamp("invalidado_en");
        return new CambioMfa(r.getObject("id", UUID.class), r.getObject("usuario_id", UUID.class),
                r.getObject("familia_id", UUID.class), r.getBoolean("habilitar"), r.getString("hash_token"),
                r.getTimestamp("creado_en").toInstant(), r.getTimestamp("expira_en").toInstant(),
                r.getBoolean("consumido"), invalidado == null ? null : invalidado.toInstant(), r.getObject("otp_actual_id", UUID.class));
    }
}
