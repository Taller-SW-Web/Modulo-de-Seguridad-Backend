package com.marketplace.auth_service.usuario.application.service;

import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.dto.ConsultaUsuariosQuery;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.RolesDeCuenta;
import com.marketplace.auth_service.usuario.application.dto.UsuarioServicioVista;
import com.marketplace.auth_service.usuario.application.dto.UsuarioVista;
import com.marketplace.auth_service.usuario.application.port.in.ConsultaUsuariosPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.dto.BloqueoVista;
import com.marketplace.auth_service.usuario.application.port.out.AutorizacionUsuariosPort;
import com.marketplace.auth_service.usuario.application.port.out.BloqueosConsultaPort;
import com.marketplace.auth_service.usuario.application.port.out.ConsultaIdentidadServicioPort;
import com.marketplace.auth_service.usuario.domain.exception.ConsultaInvalidaException;
import com.marketplace.auth_service.usuario.domain.exception.CuentaNoEncontradaException;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Listado y detalle administrativos de cuentas (EP-01-T003/T004).
 *
 * <p>Orden fijo: <b>autorizar</b> con los permisos actuales, <b>validar</b> los
 * parámetros, <b>consultar</b> cuentas con los filtros aplicados antes de contar
 * y paginar, y <b>proyectar</b> los roles de toda la página en una sola consulta y el
 * bloqueo vigente (RF-15.6) solo de las cuentas en estado BLOQUEADO.
 * Sin Spring, JPA ni HTTP.
 */
public final class ConsultaUsuariosService implements ConsultaUsuariosPort {

    static final int TAMANO_POR_DEFECTO = 20;
    static final int TAMANO_MAXIMO = 100;
    static final int CORREO_MAXIMO = 254;

    private final CuentasPort cuentas;
    private final AutorizacionUsuariosPort autorizacion;
    private final BloqueosConsultaPort bloqueos;
    private final ConsultaIdentidadServicioPort identidadServicio;

    public ConsultaUsuariosService(CuentasPort cuentas, AutorizacionUsuariosPort autorizacion,
                                   BloqueosConsultaPort bloqueos) {
        this(cuentas, autorizacion, bloqueos, (actor, idTexto) -> {
            throw new UnsupportedOperationException("ConsultaIdentidadServicioPort no configurado");
        });
    }

    public ConsultaUsuariosService(CuentasPort cuentas, AutorizacionUsuariosPort autorizacion,
                                   BloqueosConsultaPort bloqueos, ConsultaIdentidadServicioPort identidadServicio) {
        this.cuentas = Objects.requireNonNull(cuentas, "cuentas");
        this.autorizacion = Objects.requireNonNull(autorizacion, "autorizacion");
        this.bloqueos = Objects.requireNonNull(bloqueos, "bloqueos");
        this.identidadServicio = Objects.requireNonNull(identidadServicio, "identidadServicio");
    }

    @Override
    public PaginaResultado<UsuarioVista> listar(ActorUsuario actor, ConsultaUsuariosQuery consulta) {
        Objects.requireNonNull(actor, "actor");
        autorizacion.exigirVerUsuarios(actor);
        ConsultaUsuariosQuery parametros = consulta == null ? ConsultaUsuariosQuery.sinFiltros() : consulta;

        FiltroCuenta filtro = new FiltroCuenta(estado(parametros.estado()), rol(parametros.rol()),
                correo(parametros.correo()));
        Paginacion paginacion = new Paginacion(entero("pagina", parametros.pagina(), 1, Integer.MAX_VALUE, 1),
                entero("tamano", parametros.tamano(), 1, TAMANO_MAXIMO, TAMANO_POR_DEFECTO));

        PaginaResultado<CuentaSnapshot> pagina = cuentas.consultar(filtro, paginacion);
        Set<UUID> ids = new LinkedHashSet<>();
        pagina.elementos().forEach(c -> ids.add(c.id()));
        Map<UUID, RolesDeCuenta> roles = autorizacion.rolesDe(ids);
        Map<UUID, BloqueoVista> bloqueados = bloqueosDe(pagina.elementos());
        List<UsuarioVista> usuarios = pagina.elementos().stream()
                .map(c -> vista(c, roles.getOrDefault(c.id(), RolesDeCuenta.NINGUNO), bloqueados.get(c.id())))
                .toList();
        return new PaginaResultado<>(usuarios, pagina.pagina(), pagina.tamano(), pagina.total());
    }

    @Override
    public UsuarioVista obtener(ActorUsuario actor, String idTexto) {
        Objects.requireNonNull(actor, "actor");
        autorizacion.exigirVerUsuarios(actor);
        UUID id = uuid(idTexto);
        CuentaSnapshot cuenta = cuentas.leer(id).orElseThrow(() -> new CuentaNoEncontradaException(id));
        RolesDeCuenta roles = autorizacion.rolesDe(Set.of(id)).getOrDefault(id, RolesDeCuenta.NINGUNO);
        return vista(cuenta, roles, bloqueosDe(List.of(cuenta)).get(id));
    }

    @Override
    public UsuarioServicioVista obtenerParaServicio(ActorServicio actor, String idTexto) {
        Objects.requireNonNull(actor, "actor");
        return identidadServicio.obtener(actor, idTexto);
    }

    /** Solo se consulta protección por las cuentas cuyo estado efectivo es BLOQUEADO. */
    private Map<UUID, BloqueoVista> bloqueosDe(List<CuentaSnapshot> cuentasPagina) {
        Set<UUID> bloqueadas = new LinkedHashSet<>();
        cuentasPagina.stream().filter(c -> EstadoCuenta.BLOQUEADO.name().equals(c.estadoEfectivo()))
                .forEach(c -> bloqueadas.add(c.id()));
        return bloqueadas.isEmpty() ? Map.of() : bloqueos.vigentes(bloqueadas);
    }

    private static UsuarioVista vista(CuentaSnapshot c, RolesDeCuenta roles, BloqueoVista bloqueo) {
        // MFA efectivo de RF-10.1: bandera voluntaria o cualquier rol de gestión.
        // Se reemplaza por MfaPort.esEfectivo cuando segundo factor lo exporte.
        boolean mfaEfectivo = c.mfaHabilitado() || roles.deGestion();
        return new UsuarioVista(c.id(), c.nombres(), c.apellidos(), c.nombreCompleto(), c.correo(), c.celular(),
                c.estadoEfectivo(), c.correoVerificado(), c.celularVerificado(), mfaEfectivo, roles.roles(),
                bloqueo);
    }

    private static String estado(String valor) {
        if (valor == null) {
            return null;
        }
        for (EstadoCuenta estado : EstadoCuenta.values()) {
            if (estado.name().equals(valor)) {
                return valor;
            }
        }
        throw new ConsultaInvalidaException("estado", "No pertenece a EstadoCuenta.");
    }

    private static String rol(String valor) {
        if (valor == null) {
            return null;
        }
        return Rol.buscar(valor).map(Rol::name)
                .orElseThrow(() -> new ConsultaInvalidaException("rol", "No pertenece al catálogo cerrado de roles."));
    }

    private static String correo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String fragmento = valor.strip();
        if (fragmento.length() > CORREO_MAXIMO) {
            throw new ConsultaInvalidaException("correo", "Máximo " + CORREO_MAXIMO + " caracteres.");
        }
        return fragmento;
    }

    private static int entero(String campo, String valor, int minimo, int maximo, int porDefecto) {
        if (valor == null) {
            return porDefecto;
        }
        int numero;
        try {
            numero = Integer.parseInt(valor.strip());
        } catch (NumberFormatException e) {
            throw new ConsultaInvalidaException(campo, "Debe ser un número entero.");
        }
        if (numero < minimo || numero > maximo) {
            throw new ConsultaInvalidaException(campo, maximo == Integer.MAX_VALUE
                    ? "Debe ser mayor o igual que " + minimo + "."
                    : "Debe estar entre " + minimo + " y " + maximo + ".");
        }
        return numero;
    }

    private static UUID uuid(String valor) {
        try {
            return UUID.fromString(Objects.requireNonNull(valor, "id").strip());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ConsultaInvalidaException("id", "No es un identificador válido.");
        }
    }
}
