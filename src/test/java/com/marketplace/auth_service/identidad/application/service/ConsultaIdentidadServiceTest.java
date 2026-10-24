package com.marketplace.auth_service.identidad.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.identidad.application.dto.CuentaIdentidad;
import com.marketplace.auth_service.identidad.application.dto.DocumentoIdentidad;
import com.marketplace.auth_service.identidad.application.dto.ResultadoConsultaIdentidad;
import com.marketplace.auth_service.identidad.application.port.out.AuditoriaIdentidadPort;
import com.marketplace.auth_service.identidad.application.port.out.CuentasIdentidadPort;
import com.marketplace.auth_service.identidad.application.port.out.DocumentoIdentidadPort;
import com.marketplace.auth_service.identidad.application.port.out.RolesIdentidadPort;
import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.shared.domain.ScopeServicio;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Núcleo de SPEC-18 ESC-18.1/18.5/18.6: documento enmascarado o en claro, y su auditoría. */
class ConsultaIdentidadServiceTest {

    private static final UUID CLIENTE = UUID.randomUUID();
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-23T20:00:00Z"), ZoneOffset.UTC);
    private static final CuentaIdentidad CUENTA = new CuentaIdentidad(CLIENTE, "Ana", "Pérez", "Ana Pérez",
            "ana@clientes.test", "+51987654321", "ACTIVO", true, true);

    private record Auditado(String accion, String clientId, UUID objetivo) {
    }

    private static ConsultaIdentidadService servicio(List<Auditado> registros,
                                                       Optional<DocumentoIdentidad> enmascarado,
                                                       Optional<DocumentoIdentidad> enClaro) {
        CuentasIdentidadPort cuentas = id -> id.equals(CLIENTE) ? Optional.of(CUENTA) : Optional.empty();
        RolesIdentidadPort roles = id -> List.of("CLIENTE");
        DocumentoIdentidadPort documentos = new DocumentoIdentidadPort() {
            @Override
            public Optional<DocumentoIdentidad> proyectar(UUID usuarioId) {
                return enmascarado;
            }

            @Override
            public Optional<DocumentoIdentidad> leerEnClaro(UUID usuarioId) {
                return enClaro;
            }
        };
        AuditoriaIdentidadPort auditoria = new AuditoriaIdentidadPort() {
            @Override
            public void moduloConsultoUsuario(String clientId, UUID objetivo, Instant fecha) {
                registros.add(new Auditado("MODULO_CONSULTO_USUARIO", clientId, objetivo));
            }

            @Override
            public void moduloObtuvoDocumento(String clientId, UUID objetivo, Instant fecha) {
                registros.add(new Auditado("MODULO_OBTUVO_DOCUMENTO", clientId, objetivo));
            }
        };
        return new ConsultaIdentidadService(cuentas, roles, documentos, auditoria, RELOJ);
    }

    @Test
    void sinScopeDeDocumentoLoDevuelveEnmascaradoYAuditaSoloLaConsulta() {
        List<Auditado> registros = new ArrayList<>();
        ConsultaIdentidadService servicio = servicio(registros,
                Optional.of(new DocumentoIdentidad("DNI", "*****234")), Optional.empty());
        ActorServicio actor = new ActorServicio("despacho", UUID.randomUUID(), Set.of("usuarios:leer"));

        var resultado = (ResultadoConsultaIdentidad.Encontrado) servicio.obtener(actor, CLIENTE.toString());

        assertThat(resultado.usuario().documentoEnmascarado()).isEqualTo("*****234");
        assertThat(resultado.usuario().documento()).isNull();
        assertThat(resultado.usuario().tipoDocumento()).isEqualTo("DNI");
        assertThat(resultado.usuario().roles()).containsExactly("CLIENTE");
        assertThat(registros).containsExactly(new Auditado("MODULO_CONSULTO_USUARIO", "despacho", CLIENTE));
    }

    @Test
    void conScopeDeDocumentoLoDevuelveEnClaroYAuditaAmbosHechos() {
        List<Auditado> registros = new ArrayList<>();
        ConsultaIdentidadService servicio = servicio(registros,
                Optional.of(new DocumentoIdentidad("DNI", "*****234")),
                Optional.of(new DocumentoIdentidad("DNI", "12345234")));
        ActorServicio actor = new ActorServicio("ventas", UUID.randomUUID(),
                Set.of(ScopeServicio.USUARIOS_LEER.codigo(), ScopeServicio.USUARIOS_LEER_DOCUMENTO.codigo()));

        var resultado = (ResultadoConsultaIdentidad.Encontrado) servicio.obtener(actor, CLIENTE.toString());

        assertThat(resultado.usuario().documento()).isEqualTo("12345234");
        assertThat(resultado.usuario().documentoEnmascarado()).isNull();
        assertThat(resultado.usuario().tipoDocumento()).isEqualTo("DNI");
        assertThat(registros).containsExactly(
                new Auditado("MODULO_OBTUVO_DOCUMENTO", "ventas", CLIENTE),
                new Auditado("MODULO_CONSULTO_USUARIO", "ventas", CLIENTE));
    }

    @Test
    void sinDocumentoRegistradoNoHayEnmascaradoNiEnClaroNiTipo() {
        List<Auditado> registros = new ArrayList<>();
        ConsultaIdentidadService servicio = servicio(registros, Optional.empty(), Optional.empty());
        ActorServicio actor = new ActorServicio("chatbot", UUID.randomUUID(), Set.of("usuarios:leer"));

        var resultado = (ResultadoConsultaIdentidad.Encontrado) servicio.obtener(actor, CLIENTE.toString());

        assertThat(resultado.usuario().documento()).isNull();
        assertThat(resultado.usuario().documentoEnmascarado()).isNull();
        assertThat(resultado.usuario().tipoDocumento()).isNull();
    }

    @Test
    void idInexistenteNoEsUnaExcepcionSinoNoEncontrado() {
        ConsultaIdentidadService servicio = servicio(new ArrayList<>(), Optional.empty(), Optional.empty());
        ActorServicio actor = new ActorServicio("despacho", UUID.randomUUID(), Set.of("usuarios:leer"));
        UUID otro = UUID.randomUUID();

        var resultado = servicio.obtener(actor, otro.toString());

        assertThat(resultado).isEqualTo(new ResultadoConsultaIdentidad.NoEncontrado(otro));
    }

    @Test
    void idMalformadoEsInvalidaNoNoEncontrado() {
        ConsultaIdentidadService servicio = servicio(new ArrayList<>(), Optional.empty(), Optional.empty());
        ActorServicio actor = new ActorServicio("despacho", UUID.randomUUID(), Set.of("usuarios:leer"));

        var resultado = servicio.obtener(actor, "no-es-uuid");

        assertThat(resultado).isEqualTo(new ResultadoConsultaIdentidad.Invalida("id", "No es un identificador válido."));
    }
}
