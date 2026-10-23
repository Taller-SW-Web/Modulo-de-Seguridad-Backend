package com.marketplace.auth_service.identidad.adapter.out.persistence;

import com.marketplace.auth_service.identidad.application.port.out.ClientesServicioPort;
import com.marketplace.auth_service.identidad.domain.model.ClienteServicio;
import com.marketplace.auth_service.identidad.domain.model.ScopeConcedido;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lectura de {@code cliente_servicio}, {@code cliente_servicio_scope} y
 * {@code scope} (tablas de identidad, SPEC-17) con una sola consulta
 * parametrizada. Solo lectura: el aprovisionamiento lo hace operaciones con su
 * runbook, nunca el runtime. Un scope concedido que no está en el catálogo no
 * se emite (el JOIN interno lo descarta).
 */
public class ClientesServicioJdbcAdapter implements ClientesServicioPort {

    private static final String CONSULTA = """
            SELECT c.client_id, c.client_secret_hash, c.activo, s.codigo, s.audiencia
              FROM cliente_servicio c
              LEFT JOIN (cliente_servicio_scope cs JOIN scope s ON s.codigo = cs.scope)
                     ON cs.client_id = c.client_id
             WHERE c.client_id = ?
            """;

    private final JdbcTemplate jdbc;

    public ClientesServicioJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<ClienteServicio> buscar(String clientId) {
        List<Fila> filas = jdbc.query(CONSULTA, (r, i) -> new Fila(r.getString("client_id"),
                r.getString("client_secret_hash"), r.getBoolean("activo"), r.getString("codigo"),
                r.getString("audiencia")), clientId);
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        List<ScopeConcedido> scopes = new ArrayList<>();
        for (Fila fila : filas) {
            if (fila.codigo() != null) {
                scopes.add(new ScopeConcedido(fila.codigo(), fila.audiencia()));
            }
        }
        Fila primera = filas.get(0);
        return Optional.of(new ClienteServicio(primera.clientId(), primera.secretoHash(), primera.activo(), scopes));
    }

    private record Fila(String clientId, String secretoHash, boolean activo, String codigo, String audiencia) {
        @Override
        public String toString() {
            return "Fila[clientId=" + clientId + ", REDACTADO]";
        }
    }
}
