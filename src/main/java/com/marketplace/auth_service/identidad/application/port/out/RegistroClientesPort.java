package com.marketplace.auth_service.identidad.application.port.out;

import com.marketplace.auth_service.identidad.application.dto.ClienteTecnico;
import com.marketplace.auth_service.identidad.application.dto.ScopeDisponible;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Escritura y lectura administrativa de {@code cliente_servicio},
 * {@code cliente_servicio_scope} y {@code scope} (SPEC-19). Nunca devuelve el hash.
 */
public interface RegistroClientesPort {

    List<ClienteTecnico> listar();

    Optional<ClienteTecnico> buscar(String clientId);

    List<ScopeDisponible> catalogo();

    /** De los códigos dados, los que están en el catálogo. */
    Set<String> catalogados(Collection<String> codigos);

    /** Inserta el cliente activo; {@code false} si el {@code clientId} ya existía. */
    boolean insertar(String clientId, String nombre, String secretoHash);

    /**
     * Bloquea la fila del cliente hasta el fin de la transacción y devuelve si
     * está activo, o vacío si no existe.
     */
    Optional<Boolean> bloquear(String clientId);

    void actualizarSecreto(String clientId, String secretoHash);

    void actualizarActivo(String clientId, boolean activo);

    /** {@code true} si la concesión es nueva. */
    boolean conceder(String clientId, String scope);

    /** {@code true} si había una concesión que se quitó. */
    boolean revocar(String clientId, String scope);
}
