package com.marketplace.auth_service.identidad.adapter.out.persistence;

import com.marketplace.auth_service.identidad.application.dto.ClienteTecnico;
import com.marketplace.auth_service.identidad.application.dto.ScopeAsignado;
import com.marketplace.auth_service.identidad.application.dto.ScopeDisponible;
import com.marketplace.auth_service.identidad.application.port.out.RegistroClientesPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Administración de clientes técnicos sobre las tablas de SPEC-17 (SPEC-19).
 * Consultas parametrizadas; ninguna lectura selecciona {@code client_secret_hash}.
 * La emisión de tokens ({@link ClientesServicioJdbcAdapter}) lee las mismas
 * tablas sin caché, así un cambio vale desde la siguiente emisión (RF-19.12).
 */
public class RegistroClientesJdbcAdapter implements RegistroClientesPort {

    private static final String LISTADO = """
            SELECT c.client_id, c.nombre, c.activo, s.codigo, s.audiencia
              FROM cliente_servicio c
              LEFT JOIN (cliente_servicio_scope cs JOIN scope s ON s.codigo = cs.scope)
                     ON cs.client_id = c.client_id
            """;

    private final JdbcTemplate jdbc;

    public RegistroClientesJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<ClienteTecnico> listar() {
        return agrupar(jdbc.query(LISTADO + " ORDER BY c.client_id, s.audiencia, s.codigo", this::fila));
    }

    @Override
    public Optional<ClienteTecnico> buscar(String clientId) {
        return agrupar(jdbc.query(LISTADO + " WHERE c.client_id = ? ORDER BY s.audiencia, s.codigo", this::fila,
                clientId)).stream().findFirst();
    }

    @Override
    public List<ScopeDisponible> catalogo() {
        return jdbc.query("SELECT codigo, audiencia, descripcion FROM scope ORDER BY audiencia, codigo",
                (r, i) -> new ScopeDisponible(r.getString("codigo"), r.getString("audiencia"),
                        r.getString("descripcion")));
    }

    @Override
    public Set<String> catalogados(Collection<String> codigos) {
        if (codigos.isEmpty()) {
            return Set.of();
        }
        String[] valores = codigos.toArray(String[]::new);
        return new HashSet<>(jdbc.query(con -> {
            var sentencia = con.prepareStatement("SELECT codigo FROM scope WHERE codigo = ANY (?)");
            sentencia.setArray(1, con.createArrayOf("varchar", valores));
            return sentencia;
        }, (r, i) -> r.getString(1)));
    }

    @Override
    public boolean insertar(String clientId, String nombre, String secretoHash) {
        return jdbc.update("""
                INSERT INTO cliente_servicio (client_id, client_secret_hash, nombre, activo)
                VALUES (?, ?, ?, true)
                ON CONFLICT (client_id) DO NOTHING
                """, clientId, secretoHash, nombre) == 1;
    }

    @Override
    public Optional<Boolean> bloquear(String clientId) {
        return jdbc.query("SELECT activo FROM cliente_servicio WHERE client_id = ? FOR UPDATE",
                (r, i) -> r.getBoolean(1), clientId).stream().findFirst();
    }

    @Override
    public void actualizarSecreto(String clientId, String secretoHash) {
        jdbc.update("UPDATE cliente_servicio SET client_secret_hash = ? WHERE client_id = ?", secretoHash, clientId);
    }

    @Override
    public void actualizarActivo(String clientId, boolean activo) {
        jdbc.update("UPDATE cliente_servicio SET activo = ? WHERE client_id = ?", activo, clientId);
    }

    @Override
    public boolean conceder(String clientId, String scope) {
        return jdbc.update("""
                INSERT INTO cliente_servicio_scope (client_id, scope) VALUES (?, ?)
                ON CONFLICT (client_id, scope) DO NOTHING
                """, clientId, scope) == 1;
    }

    @Override
    public boolean revocar(String clientId, String scope) {
        return jdbc.update("DELETE FROM cliente_servicio_scope WHERE client_id = ? AND scope = ?",
                clientId, scope) == 1;
    }

    private Fila fila(java.sql.ResultSet r, int i) throws java.sql.SQLException {
        return new Fila(r.getString("client_id"), r.getString("nombre"), r.getBoolean("activo"),
                r.getString("codigo"), r.getString("audiencia"));
    }

    private static List<ClienteTecnico> agrupar(List<Fila> filas) {
        Map<String, List<Fila>> porCliente = new LinkedHashMap<>();
        for (Fila fila : filas) {
            porCliente.computeIfAbsent(fila.clientId(), k -> new ArrayList<>()).add(fila);
        }
        List<ClienteTecnico> clientes = new ArrayList<>();
        for (List<Fila> grupo : porCliente.values()) {
            Fila primera = grupo.get(0);
            List<ScopeAsignado> scopes = grupo.stream().filter(f -> f.codigo() != null)
                    .map(f -> new ScopeAsignado(f.codigo(), f.audiencia())).toList();
            clientes.add(new ClienteTecnico(primera.clientId(), primera.nombre(), primera.activo(), scopes));
        }
        return clientes;
    }

    private record Fila(String clientId, String nombre, boolean activo, String codigo, String audiencia) {
    }
}
