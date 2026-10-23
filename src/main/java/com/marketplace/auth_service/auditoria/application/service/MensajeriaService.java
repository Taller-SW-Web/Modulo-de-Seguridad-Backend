package com.marketplace.auth_service.auditoria.application.service;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.auditoria.application.port.out.CifradoMensajesPort;
import com.marketplace.auth_service.auditoria.application.port.out.EnvioCorreoPort;
import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import com.marketplace.auth_service.auditoria.application.port.out.PublicadorEventosPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Mensajería por outbox (TEC-INFRA-T003/T004). El productor guarda en su commit; el worker
 * reclama correos en lote (SKIP LOCKED, lease 60 s), los descifra y los envía fuera de locks,
 * con un intento inicial y tres reintentos (10, 30, 60 s); FALLIDO al cuarto fallo.
 * Los eventos públicos solo se marcan ENVIADO después de la confirmación del broker.
 */
public final class MensajeriaService implements MensajeriaPort {

    public static final String PREFIJO_CORREO = "correo.";
    static final int LOTE = 20;
    static final Duration LEASE = Duration.ofSeconds(60);
    private static final List<Duration> ESPERAS_REINTENTO = List.of(
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(60));
    static final int MAX_INTENTOS = 4;

    private final OutboxPort outbox;
    private final CifradoMensajesPort cifrado;
    private final EnvioCorreoPort envio;
    private final Clock reloj;

    public MensajeriaService(OutboxPort outbox, CifradoMensajesPort cifrado, EnvioCorreoPort envio, Clock reloj) {
        this.outbox = Objects.requireNonNull(outbox);
        this.cifrado = Objects.requireNonNull(cifrado);
        this.envio = Objects.requireNonNull(envio);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public UUID guardarEvento(EventoUsuario evento) {
        Map<String, String> payload = new HashMap<>(evento.carga());
        payload.put("eventoId", evento.eventoId().toString());
        payload.put("tipo", evento.topico());
        outbox.guardar(new OutboxPort.Mensaje(evento.eventoId(), evento.topico(), payload, 0));
        return evento.eventoId();
    }

    @Override
    public UUID guardarCorreo(EntregaCorreo correo) {
        String topico = correo.topico().startsWith(PREFIJO_CORREO) ? correo.topico() : PREFIJO_CORREO + correo.topico();
        outbox.guardar(new OutboxPort.Mensaje(correo.mensajeId(), topico,
                Map.of("destino", cifrado.cifrar(correo.destino()), "contenido", cifrado.cifrar(correo.contenido())), 0));
        return correo.mensajeId();
    }

    /** Un ciclo del worker; devuelve cuántos correos se entregaron. */
    public int procesarCorreos() {
        Instant ahora = reloj.instant();
        int entregados = 0;
        for (OutboxPort.Mensaje m : outbox.reclamarCorreos(LOTE, ahora, ahora.plus(LEASE))) {
            try {
                envio.enviar(cifrado.descifrar(m.payload().get("destino")), asunto(m.topico()),
                        cifrado.descifrar(m.payload().get("contenido")));
                outbox.marcarEnviado(m.id(), reloj.instant());
                entregados++;
            } catch (RuntimeException e) {
                registrarFallo(m, e);
            }
        }
        return entregados;
    }

    /**
     * Un ciclo del publicador de eventos (TEC-INFRA-T004): reclama eventos pendientes y los publica
     * con el sobre del catálogo; marca ENVIADO solo tras la confirmación del broker.
     */
    public int procesarEventos(PublicadorEventosPort publicador) {
        Instant ahora = reloj.instant();
        int publicados = 0;
        for (OutboxPort.Mensaje m : outbox.reclamarEventos(LOTE, ahora, ahora.plus(LEASE))) {
            try {
                publicador.publicar(m.topico(), sobre(m));
                outbox.marcarEnviado(m.id(), reloj.instant());
                publicados++;
            } catch (RuntimeException e) {
                registrarFallo(m, e);
            }
        }
        return publicados;
    }

    private void registrarFallo(OutboxPort.Mensaje mensaje, RuntimeException fallo) {
        int intento = mensaje.intentos() + 1;
        boolean definitivo = intento >= MAX_INTENTOS;
        Instant proximo = definitivo ? null : reloj.instant().plus(ESPERAS_REINTENTO.get(intento - 1));
        outbox.marcarFallo(mensaje.id(), fallo.getClass().getSimpleName(), proximo, definitivo);
    }

    /** Sobre común del catálogo: eventoId, tipo, version, fecha, usuarioId y datos propios de cada tipo. */
    static Map<String, Object> sobre(OutboxPort.Mensaje m) {
        Map<String, String> carga = m.payload();
        Map<String, Object> sobre = new java.util.LinkedHashMap<>();
        sobre.put("eventoId", carga.getOrDefault("eventoId", m.id().toString()));
        sobre.put("tipo", m.topico());
        sobre.put("version", 1);
        sobre.put("fecha", carga.get("fecha"));
        sobre.put("usuarioId", carga.get("usuarioId"));
        Map<String, Object> datos = new java.util.LinkedHashMap<>();
        switch (m.topico()) {
            case "usuario.bloqueado" -> {
                datos.put("automatico", !"false".equals(carga.getOrDefault("automatico", "true")));
                datos.put("hasta", carga.get("hasta"));
            }
            case "usuario.desbloqueado" -> datos.put("via", carga.getOrDefault("via", "ENLACE"));
            case "usuario.roles_cambiados" -> {
                String roles = carga.getOrDefault("roles", "");
                datos.put("roles", roles.isBlank() ? java.util.List.of() : java.util.List.of(roles.split(",")));
            }
            default -> carga.forEach((clave, valor) -> {
                if (!java.util.Set.of("eventoId", "tipo", "fecha", "usuarioId").contains(clave)) {
                    datos.put(clave, valor);
                }
            });
        }
        sobre.put("datos", datos);
        return sobre;
    }

    static String asunto(String topico) {
        return switch (topico) {
            case "correo.cuenta_bloqueada" -> "Bloqueamos tu cuenta por seguridad";
            case "correo.cuenta_desbloqueada" -> "Tu cuenta fue desbloqueada";
            case "correo.otp" -> "Tu código de verificación";
            case "correo.verificacion_correo" -> "Confirma tu correo";
            default -> "Aviso de seguridad de tu cuenta";
        };
    }
}
