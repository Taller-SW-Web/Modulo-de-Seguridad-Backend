package com.marketplace.auth_service.auditoria.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesAesGcm;
import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesDenegado;
import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.out.AuditoriaPersistenciaPort;
import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import com.marketplace.auth_service.auditoria.domain.exception.AccionNoCatalogadaException;
import com.marketplace.auth_service.auditoria.domain.exception.DetalleInseguroException;
import com.marketplace.auth_service.auditoria.domain.exception.MensajeriaNoDisponibleException;
import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** EP-06-T002/T004 y TEC-INFRA-T003/T004 con dobles en memoria y AES-GCM real. */
class MensajeriaYAuditoriaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-17T15:00:00Z");
    private static final UUID TITULAR = UUID.randomUUID();

    private final CifradoMensajesAesGcm aes = new CifradoMensajesAesGcm("k1", new byte[32]);

    // ---------- auditoría

    private static final class Registros implements AuditoriaPersistenciaPort {
        final List<RegistroAuditoria> filas = new ArrayList<>();
        @Override public long guardar(RegistroAuditoria r) { filas.add(r); return filas.size(); }
        @Override public boolean existeRegistro(UUID id) { return filas.stream().anyMatch(r -> r.registroId().equals(id)); }
        @Override public Pagina listarDeObjetivo(UUID objetivo, int pagina, int tamano) {
            var propios = filas.stream().filter(r -> objetivo.equals(r.objetivoUsuarioId())).toList();
            return new Pagina(propios.stream().skip((long) (pagina - 1) * tamano).limit(tamano).toList(), propios.size());
        }
    }

    private static RegistroSeguro registro(UUID id, String accion, UUID objetivo, Map<String, String> detalle) {
        return new RegistroSeguro(id, accion, "EXITO", "USUARIO", "sub", objetivo, AHORA, "10.0.0.1", "jdk", detalle);
    }

    @Test
    void criticaSeGuardaYaYValidaCatalogoYSecretos() {
        var registros = new Registros();
        var servicio = new AuditoriaService(registros, Runnable::run);

        servicio.registrarCritica(registro(UUID.randomUUID(), "ROL_ASIGNADO", TITULAR, Map.of("rol", "VENDEDOR")));

        assertThat(registros.filas).hasSize(1);
        assertThatThrownBy(() -> servicio.registrarCritica(registro(UUID.randomUUID(), "INVENTADA", TITULAR, Map.of())))
                .isInstanceOf(AccionNoCatalogadaException.class);
        assertThatThrownBy(() -> servicio.registrarCritica(registro(UUID.randomUUID(), "SESION_INICIADA", TITULAR,
                Map.of("refreshToken", "x")))).isInstanceOf(DetalleInseguroException.class);
    }

    @Test
    void noCriticaEsperaAlCommitYDeduplicaReintentos() {
        var registros = new Registros();
        List<Runnable> pendientes = new ArrayList<>();
        var servicio = new AuditoriaService(registros, pendientes::add);
        UUID id = UUID.randomUUID();

        servicio.registrarNoCriticaTrasCommit(registro(id, "ACCESO_DENEGADO", TITULAR, Map.of()));
        servicio.registrarNoCriticaTrasCommit(registro(id, "ACCESO_DENEGADO", TITULAR, Map.of()));
        assertThat(registros.filas).isEmpty();

        pendientes.forEach(Runnable::run);
        assertThat(registros.filas).hasSize(1);
    }

    @Test
    void actividadPropiaSoloDelTitularYConTamanoAcotado() {
        var registros = new Registros();
        var servicio = new AuditoriaService(registros, Runnable::run);
        for (int i = 0; i < 3; i++) {
            servicio.registrarCritica(registro(UUID.randomUUID(), "SESION_INICIADA", TITULAR, Map.of()));
        }
        servicio.registrarCritica(registro(UUID.randomUUID(), "SESION_INICIADA", UUID.randomUUID(), Map.of()));

        var pagina = servicio.actividadPropia(TITULAR, 1, 2);
        assertThat(pagina.total()).isEqualTo(3);
        assertThat(pagina.registros()).hasSize(2).allMatch(r -> TITULAR.equals(r.objetivoUsuarioId()));
        assertThat(servicio.actividadPropia(TITULAR, 0, 10_000).registros()).hasSize(3);
    }

    // ---------- outbox

    private static final class Outbox implements OutboxPort {
        final Map<UUID, Mensaje> filas = new LinkedHashMap<>();
        final Map<UUID, String> estado = new HashMap<>();
        @Override public void guardar(Mensaje m) { filas.put(m.id(), m); estado.put(m.id(), "PENDIENTE"); }
        @Override public List<Mensaje> reclamarCorreos(int lote, Instant ahora, Instant hasta) {
            return filas.values().stream().filter(m -> m.topico().startsWith("correo.")
                    && "PENDIENTE".equals(estado.get(m.id()))).limit(lote).toList();
        }
        @Override public List<Mensaje> reclamarEventos(int lote, Instant ahora, Instant hasta) {
            return filas.values().stream().filter(m -> !m.topico().startsWith("correo.")
                    && "PENDIENTE".equals(estado.get(m.id()))).limit(lote).toList();
        }
        @Override public void marcarEnviado(UUID id, Instant ahora) { estado.put(id, "ENVIADO"); filas.put(id,
                new Mensaje(id, filas.get(id).topico(), Map.of(), filas.get(id).intentos())); }
        @Override public void marcarFallo(UUID id, String error, Instant proximo, boolean definitivo) {
            var m = filas.get(id);
            filas.put(id, new Mensaje(id, m.topico(), definitivo ? Map.of() : m.payload(), m.intentos() + 1));
            if (definitivo) estado.put(id, "FALLIDO");
        }
    }

    @Test
    void correoSeGuardaCifradoYElWorkerLoEntregaYPurga() {
        var outbox = new Outbox();
        List<String> enviados = new ArrayList<>();
        var servicio = new MensajeriaService(outbox, aes, (d, a, c) -> enviados.add(d + "|" + a + "|" + c),
                Clock.fixed(AHORA, ZoneOffset.UTC));
        UUID id = UUID.randomUUID();

        servicio.guardarCorreo(new EntregaCorreo(id, "cuenta_bloqueada", "ana@g7.test", "enlace-secreto"));

        var guardado = outbox.filas.get(id);
        assertThat(guardado.topico()).isEqualTo("correo.cuenta_bloqueada");
        assertThat(guardado.payload().values()).noneMatch(v -> v.contains("ana@g7.test") || v.contains("enlace-secreto"));
        assertThat(servicio.procesarCorreos()).isEqualTo(1);
        assertThat(enviados).containsExactly("ana@g7.test|Bloqueamos tu cuenta por seguridad|enlace-secreto");
        assertThat(outbox.filas.get(id).payload()).isEmpty();
        assertThat(servicio.procesarCorreos()).isZero();
    }

    @Test
    void smtpCaidoReintentaYAlQuintoFalloQuedaFallidoSinContenido() {
        var outbox = new Outbox();
        var servicio = new MensajeriaService(outbox, aes, (d, a, c) -> { throw new IllegalStateException("smtp"); },
                Clock.fixed(AHORA, ZoneOffset.UTC));
        UUID id = UUID.randomUUID();
        servicio.guardarCorreo(new EntregaCorreo(id, "correo.otp", "ana@g7.test", "123456"));

        for (int i = 0; i < 5; i++) {
            servicio.procesarCorreos();
        }

        assertThat(outbox.estado.get(id)).isEqualTo("FALLIDO");
        assertThat(outbox.filas.get(id).intentos()).isEqualTo(5);
        assertThat(outbox.filas.get(id).payload()).isEmpty();
    }

    @Test
    void eventosQuedanPendientesParaElBrokerYSinClaveNoSeGuardaNingunCorreo() {
        var outbox = new Outbox();
        var servicio = new MensajeriaService(outbox, new CifradoMensajesDenegado(), (d, a, c) -> { },
                Clock.fixed(AHORA, ZoneOffset.UTC));
        UUID evento = UUID.randomUUID();

        servicio.guardarEvento(new EventoUsuario(evento, "usuario.bloqueado", Map.of("usuarioId", TITULAR.toString())));

        assertThat(outbox.filas.get(evento).payload()).containsEntry("tipo", "usuario.bloqueado");
        assertThat(servicio.procesarCorreos()).isZero();
        assertThat(outbox.estado.get(evento)).isEqualTo("PENDIENTE");
        assertThatThrownBy(() -> servicio.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "correo.otp", "a@g7.test", "x")))
                .isInstanceOf(MensajeriaNoDisponibleException.class);
    }

    @Test
    void aesGcmIdaYVueltaConIvDistintoYDetectaAlteraciones() {
        String a = aes.cifrar("ana@g7.test");
        String b = aes.cifrar("ana@g7.test");
        assertThat(a).isNotEqualTo(b).startsWith("k1:");
        assertThat(aes.descifrar(a)).isEqualTo("ana@g7.test");
        char[] alterado = a.toCharArray();
        alterado[alterado.length - 3] = alterado[alterado.length - 3] == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> aes.descifrar(new String(alterado))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CifradoMensajesAesGcm("k1", new byte[16])).isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> datos(Map<String, Object> sobre) {
        return (Map<String, Object>) sobre.get("datos");
    }

    @Test
    void eventosSePublicanConElSobreDelCatalogoYSoloSeMarcanTrasConfirmar() {
        var outbox = new Outbox();
        var servicio = new MensajeriaService(outbox, aes, (d, a, c) -> { }, Clock.fixed(AHORA, ZoneOffset.UTC));
        UUID bloqueo = UUID.randomUUID();
        UUID roles = UUID.randomUUID();
        servicio.guardarEvento(new EventoUsuario(bloqueo, "usuario.bloqueado", Map.of("usuarioId", TITULAR.toString(),
                "automatico", "true", "fecha", AHORA.toString())));
        servicio.guardarEvento(new EventoUsuario(roles, "usuario.roles_cambiados", Map.of("usuarioId", TITULAR.toString(),
                "roles", "CLIENTE,VENDEDOR", "fecha", AHORA.toString())));
        List<Map<String, Object>> publicados = new ArrayList<>();

        assertThat(servicio.procesarEventos((tipo, sobre) -> publicados.add(sobre))).isEqualTo(2);

        assertThat(publicados.get(0)).containsEntry("eventoId", bloqueo.toString()).containsEntry("tipo", "usuario.bloqueado")
                .containsEntry("version", 1).containsEntry("usuarioId", TITULAR.toString());
        assertThat(datos(publicados.get(0))).containsEntry("automatico", true).containsEntry("hasta", null);
        assertThat(datos(publicados.get(1))).containsEntry("roles", List.of("CLIENTE", "VENDEDOR"));
        assertThat(outbox.estado.get(bloqueo)).isEqualTo("ENVIADO");
    }

    @Test
    void brokerSinConfirmarDejaElEventoPendienteParaReintentar() {
        var outbox = new Outbox();
        var servicio = new MensajeriaService(outbox, aes, (d, a, c) -> { }, Clock.fixed(AHORA, ZoneOffset.UTC));
        UUID id = UUID.randomUUID();
        servicio.guardarEvento(new EventoUsuario(id, "usuario.desbloqueado", Map.of("usuarioId", TITULAR.toString())));

        assertThat(servicio.procesarEventos((tipo, sobre) -> { throw new IllegalStateException("sin confirmación"); }))
                .isZero();
        assertThat(outbox.estado.get(id)).isEqualTo("PENDIENTE");
        assertThat(outbox.filas.get(id).intentos()).isEqualTo(1);
    }
}
