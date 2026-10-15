package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.AtributosCliente;
import com.marketplace.auth_service.perfil.application.dto.AtributosVendedor;
import com.marketplace.auth_service.perfil.application.dto.CuentaBasica;
import com.marketplace.auth_service.perfil.application.dto.DocumentoProyectado;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.out.AutorizacionPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.CuentasPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.MfaEfectivoPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.PerfilesPersistidosPort;
import com.marketplace.auth_service.perfil.application.port.out.RolesPerfilPort;
import com.marketplace.auth_service.perfil.domain.model.CuentaPerfilNoEncontradaException;
import com.marketplace.auth_service.perfil.domain.model.PerfilCliente;
import com.marketplace.auth_service.perfil.domain.model.PerfilVendedor;
import com.marketplace.auth_service.perfil.domain.model.ProyeccionNoAutorizadaException;
import com.marketplace.auth_service.perfil.domain.model.RolesCuenta;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Lectura de perfil: el propio y la proyección para un actor autorizado. Sin Spring/HTTP/JPA. */
public final class ConsultarPerfil {
    private final CuentasPerfilPort cuentas;
    private final RolesPerfilPort roles;
    private final MfaEfectivoPerfilPort mfa;
    private final PerfilesPersistidosPort perfiles;
    private final AutorizacionPerfilPort autorizacion;
    private final ProyectorDocumento proyectorDocumento;

    public ConsultarPerfil(
            CuentasPerfilPort cuentas,
            RolesPerfilPort roles,
            MfaEfectivoPerfilPort mfa,
            PerfilesPersistidosPort perfiles,
            AutorizacionPerfilPort autorizacion,
            ProyectorDocumento proyectorDocumento) {
        this.cuentas = Objects.requireNonNull(cuentas, "cuentas");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.mfa = Objects.requireNonNull(mfa, "mfa");
        this.perfiles = Objects.requireNonNull(perfiles, "perfiles");
        this.autorizacion = Objects.requireNonNull(autorizacion, "autorizacion");
        this.proyectorDocumento = Objects.requireNonNull(proyectorDocumento, "proyectorDocumento");
    }

    /** Perfil privado del titular; el id sale del sub del actor, nunca de la petición. */
    public PerfilProyectado leerPropio(ActorUsuario actor) {
        Objects.requireNonNull(actor, "actor");
        return proyectarUno(actor.sub());
    }

    /** Autoriza primero (titular o usuario.ver) y solo entonces lee. */
    public PerfilProyectado proyectarPara(ActorUsuario actor, UUID objetivoId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(objetivoId, "objetivoId");
        if (!autorizacion.puedeVerPerfil(actor, objetivoId)) {
            throw new ProyeccionNoAutorizadaException();
        }
        return proyectarUno(objetivoId);
    }

    private PerfilProyectado proyectarUno(UUID usuarioId) {
        CuentaBasica cuenta = cuentas.leer(usuarioId).orElseThrow(() -> new CuentaPerfilNoEncontradaException(usuarioId));
        RolesCuenta rolesCuenta = new RolesCuenta(roles.rolesDe(usuarioId));
        boolean mfaEfectivo = mfa.efectivo(usuarioId);
        Optional<PerfilCliente> cliente = perfiles.clienteDe(usuarioId);
        Optional<PerfilVendedor> vendedor = perfiles.vendedorDe(usuarioId);

        AtributosCliente atributosCliente = rolesCuenta.esCliente()
                ? new AtributosCliente(cliente.flatMap(PerfilCliente::fechaNacimiento).orElse(null))
                : null;
        AtributosVendedor atributosVendedor = rolesCuenta.esVendedor()
                ? new AtributosVendedor(
                        vendedor.flatMap(PerfilVendedor::codigoVendedor).orElse(null),
                        vendedor.flatMap(PerfilVendedor::tienda).orElse(null),
                        vendedor.flatMap(PerfilVendedor::fechaIngreso).orElse(null))
                : null;
        DocumentoProyectado documento =
                proyectorDocumento.proyectar(cliente.flatMap(PerfilCliente::documento)).orElse(null);

        return new PerfilProyectado(
                cuenta.id(),
                cuenta.nombres(),
                cuenta.apellidos(),
                cuenta.nombreCompleto(),
                cuenta.correo(),
                cuenta.celular(),
                cuenta.estado(),
                cuenta.correoVerificado(),
                cuenta.celularVerificado(),
                mfaEfectivo,
                rolesCuenta.ordenados(),
                atributosCliente,
                atributosVendedor,
                documento);
    }
}
