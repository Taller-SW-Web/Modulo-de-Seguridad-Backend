package com.marketplace.auth_service.perfil.application.service;

import static com.marketplace.auth_service.perfil.application.service.DoblesPerfil.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.perfil.application.dto.CambioAdministrativo;
import com.marketplace.auth_service.perfil.application.dto.CampoEditable;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.out.AuditoriaPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.EscrituraPerfilesPort;
import com.marketplace.auth_service.perfil.application.port.out.EventosPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.PermisoEdicionAtributosPort;
import com.marketplace.auth_service.perfil.domain.model.AtributoInvalidoException;
import com.marketplace.auth_service.perfil.domain.model.AtributoNoAplicableException;
import com.marketplace.auth_service.perfil.domain.model.CuentaObjetivoNoEncontradaException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.perfil.domain.model.EdicionAdministrativaNoPermitidaException;
import com.marketplace.auth_service.perfil.domain.model.PerfilCliente;
import com.marketplace.auth_service.perfil.domain.model.PerfilVendedor;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FEAT-04-T6: edición administrativa con dobles en memoria. Sin Spring ni DB. */
class EditarAtributosAdministrativoTest {
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000ad");
    private static final UUID VENDEDOR = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CLIENTE = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC);
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("10.0.0.1", "prueba", UUID.randomUUID());

    /** Puertos nuevos de T6, escribiendo sobre los mismos mapas que DoblesPerfil. */
    static final class Extra implements PermisoEdicionAtributosPort, EscrituraPerfilesPort, EventosPerfilPort,
            AuditoriaPerfilPort {
        final DoblesPerfil d;
        final Set<UUID> conAtributosEditar = new HashSet<>();
        final List<String> eventos = new ArrayList<>();
        final List<String> auditorias = new ArrayList<>();

        Extra(DoblesPerfil d) {
            this.d = d;
        }

        @Override public boolean puedeEditarAtributos(ActorUsuario actor) {
            return conAtributosEditar.contains(actor.sub());
        }
        @Override public void guardarFechaNacimiento(UUID id, LocalDate fecha) {
            d.escrituras.add("cliente:" + id);
            d.clientes.put(id, new PerfilCliente(id, fecha, null));
        }
        @Override public void guardarVendedor(UUID id, String codigo, String tienda, LocalDate ingreso) {
            d.escrituras.add("vendedor:" + id);
            d.vendedores.put(id, new PerfilVendedor(id, codigo, tienda, ingreso));
        }
        @Override public void atributosActualizados(UUID id, List<String> campos) {
            eventos.add(id + ":" + String.join(",", campos));
        }
        @Override public void atributosActualizados(ActorUsuario actor, UUID id, List<String> campos,
                                                    ContextoPeticion contexto) {
            auditorias.add(actor.sub() + "->" + id + ":" + String.join(",", campos));
        }
    }

    private DoblesPerfil d;
    private Extra x;
    private EditarAtributosAdministrativo edicion;

    @BeforeEach
    void preparar() {
        d = new DoblesPerfil();
        d.cuenta(ADMIN, "Ada", "Admin", "+51900000000", Set.of("ADMIN_SISTEMA"), true);
        d.cuenta(VENDEDOR, "Victor", "Ventas", "+51911111111", Set.of("VENDEDOR"), false);
        d.cuenta(CLIENTE, "Carla", "Cliente", "+51922222222", Set.of("CLIENTE"), false);
        d.actoresConUsuarioVer.add(ADMIN);
        x = new Extra(d);
        x.conAtributosEditar.add(ADMIN);
        ConsultarPerfil consulta = new ConsultarPerfil(d, d, d, d, d, new ProyectorDocumento(documento -> "x"));
        edicion = new EditarAtributosAdministrativo(x, d, d, d, d, x, x, x, consulta, RELOJ);
    }

    private static <T> CampoEditable<T> om() {
        return CampoEditable.omitido();
    }

    private static <T> CampoEditable<T> pr(T v) {
        return CampoEditable.presente(v);
    }

    private static CambioAdministrativo vendedor(CampoEditable<String> codigo, CampoEditable<String> tienda,
                                                 CampoEditable<LocalDate> ingreso) {
        return new CambioAdministrativo(om(), om(), om(), om(), codigo, tienda, ingreso, false);
    }

    @Test
    void unAdminEditaLosAtributosDeVendedorYQuedanEventoYAuditoria() {
        PerfilProyectado p = edicion.actualizar(actor(ADMIN), VENDEDOR,
                vendedor(pr(" VEND-1042 "), pr("TIENDA-MIRAFLORES"), pr(LocalDate.of(2026, 3, 1))), CONTEXTO);

        assertThat(d.escrituras).containsExactly("bloquear:" + VENDEDOR, "vendedor:" + VENDEDOR);
        assertThat(p.atributosVendedor().codigoVendedor()).as("recortado").isEqualTo("VEND-1042");
        assertThat(p.atributosVendedor().tienda()).isEqualTo("TIENDA-MIRAFLORES");
        assertThat(p.atributosVendedor().fechaIngreso()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(x.eventos).containsExactly(VENDEDOR + ":codigoVendedor,tienda,fechaIngreso");
        assertThat(x.auditorias).containsExactly(ADMIN + "->" + VENDEDOR + ":codigoVendedor,tienda,fechaIngreso");
    }

    @Test
    void unCampoOmitidoConservaElValorGuardado() {
        d.vendedores.put(VENDEDOR, new PerfilVendedor(VENDEDOR, "VEND-1", "Centro", LocalDate.of(2025, 1, 2)));

        PerfilProyectado p = edicion.actualizar(actor(ADMIN), VENDEDOR, vendedor(om(), pr("Miraflores"), om()), CONTEXTO);

        assertThat(p.atributosVendedor().codigoVendedor()).isEqualTo("VEND-1");
        assertThat(p.atributosVendedor().tienda()).isEqualTo("Miraflores");
        assertThat(p.atributosVendedor().fechaIngreso()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(x.eventos).containsExactly(VENDEDOR + ":tienda");
    }

    @Test
    void sinAtributosEditarResponde403SinLeerNiEscribirNada() {
        d.lecturas = 0;

        assertThatThrownBy(() -> edicion.actualizar(actor(CLIENTE), VENDEDOR,
                vendedor(pr("VEND-9"), om(), om()), CONTEXTO))
                .isInstanceOf(EdicionAdministrativaNoPermitidaException.class);

        assertThat(d.lecturas).isZero();
        assertThat(d.escrituras).isEmpty();
        assertThat(x.eventos).isEmpty();
        assertThat(x.auditorias).isEmpty();
    }

    @Test
    void elTitularSinPermisoTampocoEditaSusAtributosDeRol() {
        assertThatThrownBy(() -> edicion.actualizar(actor(VENDEDOR), VENDEDOR,
                vendedor(om(), pr("Otra"), om()), CONTEXTO))
                .isInstanceOf(EdicionAdministrativaNoPermitidaException.class);
        assertThat(d.escrituras).isEmpty();
    }

    @Test
    void unAtributoDeVendedorEnUnaCuentaSinEseRolResponde422SinEscribir() {
        assertThatThrownBy(() -> edicion.actualizar(actor(ADMIN), CLIENTE,
                new CambioAdministrativo(pr("Carla María"), om(), om(), om(), pr("VEND-1"), om(), om(), false),
                CONTEXTO))
                .isInstanceOf(AtributoNoAplicableException.class)
                .hasMessage("codigoVendedor no aplica a una cuenta sin rol VENDEDOR.");
        assertThat(d.escrituras).as("ni siquiera los básicos válidos").isEmpty();
        assertThat(x.eventos).isEmpty();
    }

    @Test
    void laFechaDeNacimientoExigeRolCliente() {
        assertThatThrownBy(() -> edicion.actualizar(actor(ADMIN), VENDEDOR,
                new CambioAdministrativo(om(), om(), om(), pr(LocalDate.of(2000, 1, 1)), om(), om(), om(), false),
                CONTEXTO))
                .isInstanceOf(AtributoNoAplicableException.class);
    }

    @Test
    void unAdminEditaBasicosYFechaDeNacimientoDeUnCliente() {
        PerfilProyectado p = edicion.actualizar(actor(ADMIN), CLIENTE,
                new CambioAdministrativo(pr(" Carla María "), om(), om(), pr(LocalDate.of(2000, 5, 17)), om(), om(),
                        om(), false), CONTEXTO);

        assertThat(d.escrituras).containsExactly("bloquear:" + CLIENTE, "actualizar:" + CLIENTE, "cliente:" + CLIENTE);
        assertThat(p.nombres()).isEqualTo("Carla María");
        assertThat(p.atributosCliente().fechaNacimiento()).isEqualTo(LocalDate.of(2000, 5, 17));
        assertThat(x.eventos).containsExactly(CLIENTE + ":nombres,fechaNacimiento");
    }

    @Test
    void unaFechaDeNacimientoDeHoyOFuturaEsInvalida() {
        assertThatThrownBy(() -> edicion.actualizar(actor(ADMIN), CLIENTE,
                new CambioAdministrativo(om(), om(), om(), pr(LocalDate.of(2026, 11, 6)), om(), om(), om(), false),
                CONTEXTO))
                .isInstanceOf(AtributoInvalidoException.class)
                .hasMessageContaining("fechaNacimiento");
        assertThat(d.escrituras).isEmpty();
    }

    @Test
    void textosDeVendedorVaciosOLargosSonInvalidos() {
        assertThatThrownBy(() -> edicion.actualizar(actor(ADMIN), VENDEDOR, vendedor(pr("   "), om(), om()), CONTEXTO))
                .isInstanceOf(AtributoInvalidoException.class).hasMessageContaining("codigoVendedor");
        assertThatThrownBy(() -> edicion.actualizar(actor(ADMIN), VENDEDOR,
                vendedor(om(), pr("x".repeat(101)), om()), CONTEXTO))
                .isInstanceOf(AtributoInvalidoException.class).hasMessageContaining("tienda");
        assertThat(d.escrituras).isEmpty();
    }

    @Test
    void nullDejaElAtributoDeVendedorSinRegistrar() {
        d.vendedores.put(VENDEDOR, new PerfilVendedor(VENDEDOR, "VEND-1", "Centro", null));

        PerfilProyectado p = edicion.actualizar(actor(ADMIN), VENDEDOR, vendedor(pr(null), om(), om()), CONTEXTO);

        assertThat(p.atributosVendedor().codigoVendedor()).isNull();
        assertThat(p.atributosVendedor().tienda()).isEqualTo("Centro");
    }

    @Test
    void unaCuentaInexistenteResponde404() {
        assertThatThrownBy(() -> edicion.actualizar(actor(ADMIN), UUID.randomUUID(),
                vendedor(pr("VEND-1"), om(), om()), CONTEXTO))
                .isInstanceOf(CuentaObjetivoNoEncontradaException.class);
        assertThat(d.escrituras).isEmpty();
    }

    @Test
    void elDocumentoFallaCerradoMientrasNoHayaClaveAes() {
        assertThatThrownBy(() -> edicion.actualizar(actor(ADMIN), CLIENTE,
                new CambioAdministrativo(pr("Carla"), om(), om(), om(), om(), om(), om(), true), CONTEXTO))
                .isInstanceOf(DocumentoNoLegibleException.class);
        assertThat(d.escrituras).isEmpty();
    }

    @Test
    void sinCambiosNoBloqueaNiEmiteEvento() {
        PerfilProyectado p = edicion.actualizar(actor(ADMIN), VENDEDOR, vendedor(om(), om(), om()), CONTEXTO);

        assertThat(p.id()).isEqualTo(VENDEDOR);
        assertThat(d.escrituras).isEmpty();
        assertThat(x.eventos).isEmpty();
        assertThat(x.auditorias).isEmpty();
    }
}
