package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.CambioAdministrativo;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.out.AuditoriaPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.CuentasPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.EscrituraCuentaPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.EscrituraPerfilesPort;
import com.marketplace.auth_service.perfil.application.port.out.EventosPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.PerfilesPersistidosPort;
import com.marketplace.auth_service.perfil.application.port.out.PermisoEdicionAtributosPort;
import com.marketplace.auth_service.perfil.application.port.out.RolesPerfilPort;
import com.marketplace.auth_service.perfil.domain.model.AtributoNoAplicableException;
import com.marketplace.auth_service.perfil.domain.model.CuentaObjetivoNoEncontradaException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.perfil.domain.model.EdicionAdministrativaNoPermitidaException;
import com.marketplace.auth_service.perfil.domain.model.PerfilVendedor;
import com.marketplace.auth_service.perfil.domain.model.RolesCuenta;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Edición de atributos por quien tiene {@code atributos.editar} (SPEC-16 RF-16.8/16.9/16.10, FEAT-04-T6).
 *
 * <p>Orden: permiso (revalidado) → validar todo → cuenta existe → atributos aplicables a sus roles →
 * bloquear cuenta → escribir → evento en el outbox → auditoría tras el commit → releer.
 * Cualquier rechazo ocurre antes de escribir, así que no hay efecto parcial. Debe ejecutarse dentro
 * de una transacción (la abre el decorador de adapter.in.transaction).
 */
public final class EditarAtributosAdministrativo {
    private final PermisoEdicionAtributosPort permiso;
    private final CuentasPerfilPort cuentas;
    private final RolesPerfilPort roles;
    private final PerfilesPersistidosPort perfiles;
    private final EscrituraCuentaPerfilPort escrituraCuenta;
    private final EscrituraPerfilesPort escrituraPerfiles;
    private final EventosPerfilPort eventos;
    private final AuditoriaPerfilPort auditoria;
    private final ConsultarPerfil consulta;
    private final Clock reloj;

    public EditarAtributosAdministrativo(PermisoEdicionAtributosPort permiso, CuentasPerfilPort cuentas,
            RolesPerfilPort roles, PerfilesPersistidosPort perfiles, EscrituraCuentaPerfilPort escrituraCuenta,
            EscrituraPerfilesPort escrituraPerfiles, EventosPerfilPort eventos, AuditoriaPerfilPort auditoria,
            ConsultarPerfil consulta, Clock reloj) {
        this.permiso = Objects.requireNonNull(permiso, "permiso");
        this.cuentas = Objects.requireNonNull(cuentas, "cuentas");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.perfiles = Objects.requireNonNull(perfiles, "perfiles");
        this.escrituraCuenta = Objects.requireNonNull(escrituraCuenta, "escrituraCuenta");
        this.escrituraPerfiles = Objects.requireNonNull(escrituraPerfiles, "escrituraPerfiles");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.consulta = Objects.requireNonNull(consulta, "consulta");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    public PerfilProyectado actualizar(ActorUsuario actor, UUID usuarioId, CambioAdministrativo cambio,
                                       ContextoPeticion contexto) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(cambio, "cambio");
        if (!permiso.puedeEditarAtributos(actor)) {
            throw new EdicionAdministrativaNoPermitidaException();
        }
        CambioAdministrativo validado = ValidacionAtributosAdministrativos.normalizar(cambio, LocalDate.now(reloj));
        if (validado.documentoPresente()) {
            // Sin la clave AES externa no se puede cifrar ni calcular el HMAC: falla cerrado.
            throw new DocumentoNoLegibleException("No hay clave configurada para escribir el documento");
        }
        if (cuentas.leer(usuarioId).isEmpty()) {
            throw new CuentaObjetivoNoEncontradaException(usuarioId);
        }
        comprobarAplicables(usuarioId, validado);
        if (!validado.hayCambios()) {
            return consulta.proyectarPara(actor, usuarioId);
        }

        escrituraCuenta.bloquear(usuarioId);
        if (validado.basicos().hayCambios()) {
            escrituraCuenta.actualizarBasicos(usuarioId, validado.basicos());
        }
        if (validado.tocaCliente()) {
            escrituraPerfiles.guardarFechaNacimiento(usuarioId, validado.fechaNacimiento().valor());
        }
        if (validado.tocaVendedor()) {
            guardarVendedor(usuarioId, validado);
        }
        List<String> campos = validado.camposPresentes();
        eventos.atributosActualizados(usuarioId, campos);
        auditoria.atributosActualizados(actor, usuarioId, campos, contexto);
        return consulta.proyectarPara(actor, usuarioId);
    }

    private void comprobarAplicables(UUID usuarioId, CambioAdministrativo cambio) {
        RolesCuenta rolesCuenta = new RolesCuenta(roles.rolesDe(usuarioId));
        if (cambio.tocaCliente() && !rolesCuenta.esCliente()) {
            throw new AtributoNoAplicableException("fechaNacimiento", RolesCuenta.CLIENTE);
        }
        if (!rolesCuenta.esVendedor()) {
            if (cambio.codigoVendedor().presente()) {
                throw new AtributoNoAplicableException("codigoVendedor", RolesCuenta.VENDEDOR);
            }
            if (cambio.tienda().presente()) {
                throw new AtributoNoAplicableException("tienda", RolesCuenta.VENDEDOR);
            }
            if (cambio.fechaIngreso().presente()) {
                throw new AtributoNoAplicableException("fechaIngreso", RolesCuenta.VENDEDOR);
            }
        }
    }

    /** Combina con lo guardado: un campo omitido conserva su valor. */
    private void guardarVendedor(UUID usuarioId, CambioAdministrativo cambio) {
        Optional<PerfilVendedor> actual = perfiles.vendedorDe(usuarioId);
        String codigo = cambio.codigoVendedor().presente()
                ? cambio.codigoVendedor().valor()
                : actual.flatMap(PerfilVendedor::codigoVendedor).orElse(null);
        String tienda = cambio.tienda().presente()
                ? cambio.tienda().valor()
                : actual.flatMap(PerfilVendedor::tienda).orElse(null);
        LocalDate ingreso = cambio.fechaIngreso().presente()
                ? cambio.fechaIngreso().valor()
                : actual.flatMap(PerfilVendedor::fechaIngreso).orElse(null);
        escrituraPerfiles.guardarVendedor(usuarioId, codigo, tienda, ingreso);
    }
}
