package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.ClienteTecnico;
import com.marketplace.auth_service.identidad.application.dto.ResultadoGestionCliente;
import com.marketplace.auth_service.identidad.application.dto.ScopeDisponible;
import com.marketplace.auth_service.identidad.application.dto.SecretoEntregado;
import com.marketplace.auth_service.identidad.application.port.in.ClientesTecnicosPort;
import com.marketplace.auth_service.identidad.application.port.out.GobiernoClientesPort;
import com.marketplace.auth_service.identidad.application.port.out.RegistroClientesPort;
import com.marketplace.auth_service.identidad.domain.model.SecretoCliente;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.marketplace.auth_service.identidad.application.dto.ResultadoGestionCliente.correcto;
import static com.marketplace.auth_service.identidad.application.dto.ResultadoGestionCliente.invalido;
import static com.marketplace.auth_service.identidad.application.dto.ResultadoGestionCliente.rechazo;

/**
 * Gestión de clientes técnicos (SPEC-19, FEAT-14).
 *
 * <ul>
 *   <li>La autorización se decide primero y contra la base (RF-19.1): sin
 *       permiso no se sabe si un cliente existe.</li>
 *   <li>El secreto lo genera el servidor con 256 bits de {@link SecureRandom}
 *       y solo se guarda su SHA-256 ({@link SecretoCliente}), el mismo formato
 *       que verifica {@code POST /auth/token} (RF-19.4).</li>
 *   <li>Las escrituras bloquean la fila del cliente, así dos cambios
 *       simultáneos sobre el mismo cliente se serializan.</li>
 *   <li>Solo se audita lo que cambió (RF-19.8, RF-19.9); la auditoría es
 *       crítica y, si falla, la transacción revierte (RF-19.13).</li>
 * </ul>
 */
public final class GestionClientesTecnicosService implements ClientesTecnicosPort {

    /** RF-19.5. */
    static final Pattern CLIENT_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{2,49}$");
    static final int NOMBRE_MAXIMO = 100;
    static final int SCOPE_MAXIMO = 50;
    static final int BYTES_SECRETO = 32;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final RegistroClientesPort registro;
    private final GobiernoClientesPort gobierno;
    private final Clock reloj;
    private final Supplier<String> generador;

    public GestionClientesTecnicosService(RegistroClientesPort registro, GobiernoClientesPort gobierno, Clock reloj) {
        this(registro, gobierno, reloj, GestionClientesTecnicosService::secretoAleatorio);
    }

    GestionClientesTecnicosService(RegistroClientesPort registro, GobiernoClientesPort gobierno, Clock reloj,
                                   Supplier<String> generador) {
        this.registro = Objects.requireNonNull(registro, "registro");
        this.gobierno = Objects.requireNonNull(gobierno, "gobierno");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.generador = Objects.requireNonNull(generador, "generador");
    }

    /** 256 bits en base64url sin relleno: 43 caracteres. */
    static String secretoAleatorio() {
        byte[] bytes = new byte[BYTES_SECRETO];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public ResultadoGestionCliente<List<ClienteTecnico>> listar(ActorUsuario actor, ContextoPeticion contexto) {
        if (!autorizado(actor, contexto)) {
            return rechazo(ResultadoGestionCliente.SCOPE_INSUFICIENTE);
        }
        return correcto(registro.listar());
    }

    @Override
    public ResultadoGestionCliente<List<ScopeDisponible>> catalogo(ActorUsuario actor, ContextoPeticion contexto) {
        if (!autorizado(actor, contexto)) {
            return rechazo(ResultadoGestionCliente.SCOPE_INSUFICIENTE);
        }
        return correcto(registro.catalogo());
    }

    @Override
    public ResultadoGestionCliente<SecretoEntregado> crear(ActorUsuario actor, String clientId, String nombre,
                                                          List<String> scopes, ContextoPeticion contexto) {
        if (!autorizado(actor, contexto)) {
            return rechazo(ResultadoGestionCliente.SCOPE_INSUFICIENTE);
        }
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
            return invalido("clientId");
        }
        if (nombre == null || nombre.isBlank() || nombre.length() > NOMBRE_MAXIMO) {
            return invalido("nombre");
        }
        List<String> pedidos = scopes == null ? List.of() : scopes;
        Set<String> unicos = new HashSet<>();
        for (String scope : pedidos) {
            if (scope == null || scope.isBlank() || scope.length() > SCOPE_MAXIMO || !unicos.add(scope)) {
                return invalido("scopes");
            }
        }
        if (!registro.catalogados(unicos).containsAll(unicos)) {
            return invalido("scopes");
        }
        String secreto = generador.get();
        if (!registro.insertar(clientId, nombre.strip(), SecretoCliente.hash(secreto))) {
            return rechazo(ResultadoGestionCliente.CLIENTE_SERVICIO_EXISTENTE);
        }
        for (String scope : pedidos) {
            registro.conceder(clientId, scope);
        }
        Map<String, String> detalle = detalle(clientId);
        detalle.put("scopes", String.join(" ", pedidos.stream().sorted().toList()));
        gobierno.registrar("CLIENTE_SERVICIO_CREADO", actor, contexto, reloj.instant(), detalle);
        return correcto(new SecretoEntregado(actual(clientId), secreto));
    }

    @Override
    public ResultadoGestionCliente<SecretoEntregado> rotarSecreto(ActorUsuario actor, String clientId,
                                                                 ContextoPeticion contexto) {
        if (!autorizado(actor, contexto)) {
            return rechazo(ResultadoGestionCliente.SCOPE_INSUFICIENTE);
        }
        if (bloquear(clientId).isEmpty()) {
            return rechazo(ResultadoGestionCliente.NO_ENCONTRADO);
        }
        String secreto = generador.get();
        registro.actualizarSecreto(clientId, SecretoCliente.hash(secreto));
        gobierno.registrar("SECRETO_SERVICIO_ROTADO", actor, contexto, reloj.instant(), detalle(clientId));
        return correcto(new SecretoEntregado(actual(clientId), secreto));
    }

    @Override
    public ResultadoGestionCliente<Void> cambiarEstado(ActorUsuario actor, String clientId, boolean activo,
                                                      ContextoPeticion contexto) {
        if (!autorizado(actor, contexto)) {
            return rechazo(ResultadoGestionCliente.SCOPE_INSUFICIENTE);
        }
        Optional<Boolean> estado = bloquear(clientId);
        if (estado.isEmpty()) {
            return rechazo(ResultadoGestionCliente.NO_ENCONTRADO);
        }
        if (estado.get() != activo) {
            registro.actualizarActivo(clientId, activo);
            gobierno.registrar(activo ? "CLIENTE_SERVICIO_REACTIVADO" : "CLIENTE_SERVICIO_DESACTIVADO",
                    actor, contexto, reloj.instant(), detalle(clientId));
        }
        return correcto(null);
    }

    @Override
    public ResultadoGestionCliente<Void> conceder(ActorUsuario actor, String clientId, String scope,
                                                 ContextoPeticion contexto) {
        if (!autorizado(actor, contexto)) {
            return rechazo(ResultadoGestionCliente.SCOPE_INSUFICIENTE);
        }
        if (bloquear(clientId).isEmpty()) {
            return rechazo(ResultadoGestionCliente.NO_ENCONTRADO);
        }
        if (scope == null || scope.isBlank() || scope.length() > SCOPE_MAXIMO
                || !registro.catalogados(List.of(scope)).contains(scope)) {
            return invalido("scope");
        }
        if (registro.conceder(clientId, scope)) {
            registrarScope("SCOPE_CONCEDIDO", actor, clientId, scope, contexto);
        }
        return correcto(null);
    }

    @Override
    public ResultadoGestionCliente<Void> revocar(ActorUsuario actor, String clientId, String scope,
                                                ContextoPeticion contexto) {
        if (!autorizado(actor, contexto)) {
            return rechazo(ResultadoGestionCliente.SCOPE_INSUFICIENTE);
        }
        if (bloquear(clientId).isEmpty()) {
            return rechazo(ResultadoGestionCliente.NO_ENCONTRADO);
        }
        if (scope != null && registro.revocar(clientId, scope)) {
            registrarScope("SCOPE_REVOCADO", actor, clientId, scope, contexto);
        }
        return correcto(null);
    }

    private boolean autorizado(ActorUsuario actor, ContextoPeticion contexto) {
        Objects.requireNonNull(actor, "actor");
        if (gobierno.autorizado(actor)) {
            return true;
        }
        gobierno.denegado(actor, contexto, reloj.instant());
        return false;
    }

    /** Un {@code clientId} con forma inválida no puede existir: se trata como inexistente. */
    private Optional<Boolean> bloquear(String clientId) {
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
            return Optional.empty();
        }
        return registro.bloquear(clientId);
    }

    private ClienteTecnico actual(String clientId) {
        return registro.buscar(clientId)
                .orElseThrow(() -> new IllegalStateException("cliente recién escrito no encontrado"));
    }

    private void registrarScope(String accion, ActorUsuario actor, String clientId, String scope,
                                ContextoPeticion contexto) {
        Map<String, String> detalle = detalle(clientId);
        detalle.put("scope", scope);
        gobierno.registrar(accion, actor, contexto, reloj.instant(), detalle);
    }

    private static Map<String, String> detalle(String clientId) {
        Map<String, String> detalle = new LinkedHashMap<>();
        detalle.put("clientId", clientId);
        return detalle;
    }
}
