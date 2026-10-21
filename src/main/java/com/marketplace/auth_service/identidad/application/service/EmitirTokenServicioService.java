package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.ClaimsServicio;
import com.marketplace.auth_service.identidad.application.dto.ResultadoTokenServicio;
import com.marketplace.auth_service.identidad.application.dto.SolicitudTokenServicio;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.port.in.TokenServicioPort;
import com.marketplace.auth_service.identidad.application.port.out.ClientesServicioPort;
import com.marketplace.auth_service.identidad.domain.model.ClienteServicio;
import com.marketplace.auth_service.identidad.domain.model.SecretoCliente;

import java.util.Objects;
import java.util.Optional;

/**
 * Token de servicio por {@code client_credentials} (SPEC-17, ESC-17.3).
 *
 * <ul>
 *   <li>Cliente inexistente, inactivo o secreto incorrecto responden igual
 *       ({@code CLIENTE_INVALIDO}); el secreto se compara siempre, también
 *       contra un hash señuelo, para no delatar qué clientes existen.</li>
 *   <li>Se conceden <b>exactamente</b> los scopes asignados al cliente, pida
 *       los que pida (RF-17.5). {@code aud} son sus APIs dueñas (RF-17.12).</li>
 *   <li>No hay refresco: el módulo pide otro antes de que venza la hora.</li>
 * </ul>
 */
public final class EmitirTokenServicioService implements TokenServicioPort {

    private final ClientesServicioPort clientes;
    private final FirmaPort firma;

    public EmitirTokenServicioService(ClientesServicioPort clientes, FirmaPort firma) {
        this.clientes = Objects.requireNonNull(clientes, "clientes");
        this.firma = Objects.requireNonNull(firma, "firma");
    }

    @Override
    public ResultadoTokenServicio emitir(SolicitudTokenServicio solicitud) {
        Objects.requireNonNull(solicitud, "solicitud");
        if (vacio(solicitud.grantType())) {
            return new ResultadoTokenServicio.Invalida("grant_type", "Es obligatorio.");
        }
        if (!SolicitudTokenServicio.CLIENT_CREDENTIALS.equals(solicitud.grantType())) {
            return new ResultadoTokenServicio.Invalida("grant_type", "Solo se admite client_credentials.");
        }
        if (vacio(solicitud.clientId()) || vacio(solicitud.clientSecret())) {
            return new ResultadoTokenServicio.ClienteInvalido();
        }
        Optional<ClienteServicio> cliente = clientes.buscar(solicitud.clientId());
        boolean secretoCorrecto = SecretoCliente.coincide(solicitud.clientSecret(),
                cliente.map(ClienteServicio::secretoHash).orElse(SecretoCliente.HASH_SENUELO));
        if (cliente.isEmpty() || !secretoCorrecto || !cliente.get().activo()) {
            return new ResultadoTokenServicio.ClienteInvalido();
        }
        ClienteServicio autenticado = cliente.get();
        var token = firma.firmarServicio(new ClaimsServicio(autenticado.clientId(), autenticado.audiencias(),
                autenticado.codigosScopes()));
        return new ResultadoTokenServicio.Emitido(token, autenticado.codigosScopes());
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
