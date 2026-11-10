package com.marketplace.auth_service.identidad.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El permiso se revalida en la base y la auditoría sale con el formato de SPEC-12 (SPEC-19). */
class GobiernoClientesAdapterTest {

    final AutorizacionPort autorizacion = mock(AutorizacionPort.class);
    final AuditoriaPort auditoria = mock(AuditoriaPort.class);
    final GobiernoClientesAdapter puente = new GobiernoClientesAdapter(autorizacion, auditoria);
    final ActorUsuario actor = new ActorUsuario(UUID.randomUUID(), UUID.randomUUID(),
            Set.of("ADMIN_SISTEMA"), Set.of("integracion.gestionar"));
    final ContextoPeticion contexto = new ContextoPeticion("10.0.0.9", "navegador", UUID.randomUUID());

    @Test
    void autorizaSoloConRolYPermisoActualesNoConLosDelToken() {
        when(autorizacion.resolver(actor.sub())).thenReturn(
                new PermisosResueltos(Set.of(Rol.ADMIN_SISTEMA), Set.of(Permiso.INTEGRACION_GESTIONAR)));
        assertThat(puente.autorizado(actor)).isTrue();

        when(autorizacion.resolver(actor.sub())).thenReturn(
                new PermisosResueltos(Set.of(Rol.ADMIN_SISTEMA), Set.of(Permiso.USUARIO_VER)));
        assertThat(puente.autorizado(actor)).as("el claim del token no basta").isFalse();

        when(autorizacion.resolver(actor.sub())).thenReturn(PermisosResueltos.ninguno());
        assertThat(puente.autorizado(actor)).isFalse();
    }

    @Test
    void registraComoCriticaConElAdministradorComoActor() {
        Instant fecha = Instant.parse("2026-11-10T20:00:00Z");

        puente.registrar("SCOPE_CONCEDIDO", actor, contexto, fecha, Map.of("clientId", "modulo-x", "scope", "roles:leer"));

        var registro = ArgumentCaptor.forClass(RegistroSeguro.class);
        verify(auditoria).registrarCritica(registro.capture());
        assertThat(registro.getValue().accion()).isEqualTo("SCOPE_CONCEDIDO");
        assertThat(registro.getValue().resultado()).isEqualTo("EXITO");
        assertThat(registro.getValue().actorTipo()).isEqualTo("USUARIO");
        assertThat(registro.getValue().actorId()).isEqualTo(actor.sub().toString());
        assertThat(registro.getValue().objetivoUsuarioId()).isNull();
        assertThat(registro.getValue().ip()).isEqualTo("10.0.0.9");
        assertThat(registro.getValue().detalle()).containsEntry("clientId", "modulo-x").containsEntry("scope", "roles:leer");
    }

    @Test
    void laDenegacionSeRegistraTrasElCommitYSuFalloNoSePropaga() {
        doThrow(new IllegalStateException("caída")).when(auditoria).registrarNoCriticaTrasCommit(any());

        assertThatCode(() -> puente.denegado(actor, contexto, Instant.now())).doesNotThrowAnyException();

        var registro = ArgumentCaptor.forClass(RegistroSeguro.class);
        verify(auditoria).registrarNoCriticaTrasCommit(registro.capture());
        assertThat(registro.getValue().accion()).isEqualTo("ACCESO_DENEGADO");
        assertThat(registro.getValue().resultado()).isEqualTo("FALLO");
    }
}
