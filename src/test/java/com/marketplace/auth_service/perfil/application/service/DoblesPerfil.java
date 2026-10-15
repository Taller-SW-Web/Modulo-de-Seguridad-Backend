package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.CuentaBasica;
import com.marketplace.auth_service.perfil.application.port.out.AutorizacionPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.CuentasPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.EscrituraCuentaPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.MfaEfectivoPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.PerfilesPersistidosPort;
import com.marketplace.auth_service.perfil.application.port.out.RolesPerfilPort;
import com.marketplace.auth_service.perfil.domain.model.PerfilCliente;
import com.marketplace.auth_service.perfil.domain.model.PerfilVendedor;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Dobles en memoria de todos los puertos de salida de perfil. Sin Spring ni DB. */
final class DoblesPerfil
        implements CuentasPerfilPort, RolesPerfilPort, MfaEfectivoPerfilPort, PerfilesPersistidosPort,
                AutorizacionPerfilPort, EscrituraCuentaPerfilPort {
    final Map<UUID, CuentaBasica> cuentas = new HashMap<>();
    final Map<UUID, Set<String>> roles = new HashMap<>();
    final Map<UUID, Boolean> mfa = new HashMap<>();
    final Map<UUID, PerfilCliente> clientes = new HashMap<>();
    final Map<UUID, PerfilVendedor> vendedores = new HashMap<>();
    final Set<UUID> actoresConUsuarioVer = new HashSet<>();
    final List<String> escrituras = new ArrayList<>();
    final List<CambioAtributos> cambiosRecibidos = new ArrayList<>();
    int lecturas;

    static ActorUsuario actor(UUID sub) {
        return new ActorUsuario(sub, UUID.randomUUID(), Set.of(), Set.of());
    }

    void cuenta(UUID id, String nombres, String apellidos, String celular, Set<String> rolesCuenta, boolean mfaEfectivo) {
        cuentas.put(id, new CuentaBasica(id, nombres, apellidos, nombres + " " + apellidos, id + "@x.pe", celular,
                "ACTIVO", true, true));
        roles.put(id, rolesCuenta);
        mfa.put(id, mfaEfectivo);
    }

    @Override public Optional<CuentaBasica> leer(UUID id) { lecturas++; return Optional.ofNullable(cuentas.get(id)); }
    @Override public Set<String> rolesDe(UUID id) { lecturas++; return roles.getOrDefault(id, Set.of()); }
    @Override public boolean efectivo(UUID id) { lecturas++; return mfa.get(id); }
    @Override public Optional<PerfilCliente> clienteDe(UUID id) { lecturas++; return Optional.ofNullable(clientes.get(id)); }
    @Override public Optional<PerfilVendedor> vendedorDe(UUID id) { lecturas++; return Optional.ofNullable(vendedores.get(id)); }
    @Override public boolean puedeVerPerfil(ActorUsuario actor, UUID objetivo) {
        return actor.esTitularDe(objetivo) || actoresConUsuarioVer.contains(actor.sub());
    }
    @Override public void bloquear(UUID id) { escrituras.add("bloquear:" + id); }

    /** Imita a Eva: aplica solo presentes y deja celularVerificado=false si el celular cambia. */
    @Override public void actualizarBasicos(UUID id, CambioAtributos cambio) {
        escrituras.add("actualizar:" + id);
        cambiosRecibidos.add(cambio);
        CuentaBasica c = cuentas.get(id);
        String nombres = cambio.nombres().presente() ? cambio.nombres().valor() : c.nombres();
        String apellidos = cambio.apellidos().presente() ? cambio.apellidos().valor() : c.apellidos();
        String celular = cambio.celular().presente() ? cambio.celular().valor() : c.celular();
        boolean verificado = c.celularVerificado() && java.util.Objects.equals(celular, c.celular());
        cuentas.put(id, new CuentaBasica(id, nombres, apellidos, nombres + " " + apellidos, c.correo(), celular,
                c.estado(), c.correoVerificado(), verificado));
    }
}
