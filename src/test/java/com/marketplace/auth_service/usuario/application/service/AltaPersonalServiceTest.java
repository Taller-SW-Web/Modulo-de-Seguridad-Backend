package com.marketplace.auth_service.usuario.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAltaPersonal;
import com.marketplace.auth_service.usuario.application.dto.SolicitudAltaPersonal;
import com.marketplace.auth_service.usuario.application.port.out.CorreoActivacionPort;
import com.marketplace.auth_service.usuario.application.port.out.CuentasPersonalPort;
import com.marketplace.auth_service.usuario.application.port.out.EfectosAltaPersonalPort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import com.marketplace.auth_service.usuario.domain.model.CuentaPersonalNueva;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** FEAT-13-T1 (SPEC-03 RF-03.1, RF-03.4): orden del alta, reversión y auditoría, sin base de datos. */
class AltaPersonalServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-12T15:00:00Z");
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.7", "prueba", UUID.randomUUID());

    private final List<String> pasos = new ArrayList<>();
    private boolean permiso = true;
    private boolean correoLibre = true;
    private boolean colaCaida;
    private boolean revertida;
    private CuentaPersonalNueva creada;

    private final CuentasPersonalPort cuentas = new CuentasPersonalPort() {
        @Override public boolean crear(CuentaPersonalNueva cuenta) {
            pasos.add("crear");
            creada = cuenta;
            return correoLibre;
        }
        @Override public boolean bloquearSinActivar(UUID usuarioId) { throw new AssertionError(); }
        @Override public void activar(UUID usuarioId, String versionTerminos, Instant ahora) { throw new AssertionError(); }
    };

    private final EfectosAltaPersonalPort efectos = new EfectosAltaPersonalPort() {
        @Override public <T> T enTransaccion(Supplier<T> trabajo, Predicate<T> confirmar) {
            pasos.add("tx");
            T resultado = trabajo.get();
            revertida = !confirmar.test(resultado);
            return resultado;
        }
        @Override public boolean puedeCrear(UUID actor) { pasos.add("permiso"); return permiso; }
        @Override public void asignarRolInicial(UUID usuarioId, Rol rol) { pasos.add("rol:" + rol); }
        @Override public void auditarCreacion(UUID usuarioId, Rol rol, ActorUsuario actor, ContextoPeticion c, Instant a) {
            pasos.add("auditar");
        }
        @Override public void auditarRechazo(String motivo, ActorUsuario actor, ContextoPeticion c, Instant a) {
            pasos.add("rechazo:" + motivo);
        }
        @Override public void denegado(ActorUsuario actor, ContextoPeticion c, Instant a) { pasos.add("denegado"); }
    };

    private final CorreoActivacionPort correo = (usuarioId, direccion, nombres) -> {
        pasos.add("enlace");
        if (colaCaida) throw new VerificacionNoDisponibleException(new IllegalStateException());
    };

    private final AltaPersonalService servicio = new AltaPersonalService(cuentas, efectos, correo,
            Clock.fixed(AHORA, ZoneOffset.UTC));
    private final ActorUsuario admin = new ActorUsuario(UUID.randomUUID(), UUID.randomUUID(), Set.of("ADMIN_SISTEMA"), Set.of("usuario.crear"));

    private ResultadoAltaPersonal crear(Rol rol) {
        return servicio.crear(admin, new SolicitudAltaPersonal("Gina@Ejemplo.com", "Gina", "Gestora", rol), CONTEXTO);
    }

    @Test
    void creaLaCuentaAsignaElRolAuditaYEncolaElEnlaceEnUnaTransaccion() {
        var resultado = crear(Rol.GESTOR_COMERCIAL);

        assertThat(resultado).isEqualTo(new ResultadoAltaPersonal.Creada(creada.id()));
        assertThat(pasos).containsExactly("tx", "permiso", "crear", "rol:GESTOR_COMERCIAL", "auditar", "enlace");
        assertThat(revertida).isFalse();
        assertThat(creada.correo()).isEqualTo("gina@ejemplo.com");
        assertThat(creada.creadaEn()).isEqualTo(AHORA);
    }

    @Test
    void sinRolNoAsignaNinguno() {
        assertThat(crear(null)).isInstanceOf(ResultadoAltaPersonal.Creada.class);
        assertThat(pasos).containsExactly("tx", "permiso", "crear", "auditar", "enlace");
    }

    @Test
    void correoExistenteRevierteSinRolNiEnlaceYSeAuditaAparte() {
        correoLibre = false;
        assertThat(crear(Rol.VENDEDOR)).isEqualTo(ResultadoAltaPersonal.Rechazo.CORREO_NO_DISPONIBLE);
        assertThat(pasos).containsExactly("tx", "permiso", "crear", "rechazo:CORREO_NO_DISPONIBLE");
        assertThat(revertida).isTrue();
    }

    @Test
    void permisoRetiradoDentroDeLaTransaccionNoCreaNada() {
        permiso = false;
        assertThat(crear(Rol.VENDEDOR)).isEqualTo(ResultadoAltaPersonal.Rechazo.SIN_PERMISO);
        assertThat(pasos).containsExactly("tx", "permiso", "denegado");
        assertThat(creada).isNull();
    }

    @Test
    void sinColaDeCorreoPropagaElFalloYLoAudita() {
        colaCaida = true;
        assertThatThrownBy(() -> crear(Rol.VENDEDOR)).isInstanceOf(VerificacionNoDisponibleException.class);
        assertThat(pasos).endsWith("enlace", "rechazo:NO_DISPONIBLE");
    }

    @Test
    void autorizarAuditaLaDenegacion() {
        assertThat(servicio.autorizar(admin, CONTEXTO)).isTrue();
        permiso = false;
        assertThat(servicio.autorizar(admin, CONTEXTO)).isFalse();
        assertThat(pasos).containsExactly("permiso", "permiso", "denegado");
    }

    @Test
    void nuncaCreaClientes() {
        assertThatThrownBy(() -> new SolicitudAltaPersonal("c@ejemplo.com", "C", "C", Rol.CLIENTE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
