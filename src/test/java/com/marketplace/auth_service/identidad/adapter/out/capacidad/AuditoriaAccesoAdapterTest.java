package com.marketplace.auth_service.identidad.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.identidad.application.dto.DenegacionAcceso;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Un 403 de scope llega a auditoría como ACCESO_DENEGADO no crítico (SPEC-17 ESC-17.8). */
class AuditoriaAccesoAdapterTest {

    @Test
    void unaDenegacionDeModuloSeRegistraComoAccesoDenegadoNoCritico() {
        AuditoriaPort auditoria = mock(AuditoriaPort.class);
        UUID jti = UUID.randomUUID();
        Instant fecha = Instant.parse("2026-10-21T15:00:00Z");

        new AuditoriaAccesoAdapter(auditoria).accesoDenegado(new DenegacionAcceso("MODULO", "modulo-chatbot", jti,
                "GET", "/api/v1/roles", new ContextoPeticion("10.0.0.7", "curl/8", UUID.randomUUID()), fecha));

        ArgumentCaptor<RegistroSeguro> registro = ArgumentCaptor.forClass(RegistroSeguro.class);
        verify(auditoria).registrarNoCriticaTrasCommit(registro.capture());
        RegistroSeguro r = registro.getValue();
        assertThat(r.accion()).isEqualTo("ACCESO_DENEGADO");
        assertThat(r.resultado()).isEqualTo("FALLO");
        assertThat(r.actorTipo()).isEqualTo("MODULO");
        assertThat(r.actorId()).isEqualTo("modulo-chatbot");
        assertThat(r.objetivoUsuarioId()).isNull();
        assertThat(r.fecha()).isEqualTo(fecha);
        assertThat(r.ip()).isEqualTo("10.0.0.7");
        assertThat(r.agente()).isEqualTo("curl/8");
        assertThat(r.detalle()).isEqualTo(Map.of("metodo", "GET", "ruta", "/api/v1/roles", "jti", jti.toString()));
    }
}
