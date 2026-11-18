package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.out.AuditoriaPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.EscrituraCuentaPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.EventosPerfilPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Edición propia de nombres/apellidos/celular (EP-08-T004/T007, SPEC-16 RF-16.2/16.9/16.10).
 *
 * <p>Orden: validar todo → bloquear cuenta → leer valor actual → escribir → evento en el outbox →
 * auditoría tras el commit → releer. Un inválido no escribe ni emite nada. Solo se emiten los
 * campos que cambiaron de verdad, y solo sus nombres. Usa los mismos puertos que la edición
 * administrativa (FEAT-04-T6), así ambas publican el mismo sobre y la misma auditoría.
 * Debe ejecutarse dentro de una transacción (la abre el decorador de adapter.in.transaction).
 */
public final class EditarPerfilPropio {
    private final EscrituraCuentaPerfilPort escritura;
    private final ConsultarPerfil consulta;
    private final EventosPerfilPort eventos;
    private final AuditoriaPerfilPort auditoria;

    public EditarPerfilPropio(EscrituraCuentaPerfilPort escritura, ConsultarPerfil consulta,
                              EventosPerfilPort eventos, AuditoriaPerfilPort auditoria) {
        this.escritura = Objects.requireNonNull(escritura, "escritura");
        this.consulta = Objects.requireNonNull(consulta, "consulta");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
    }

    public PerfilProyectado actualizarPropio(ActorUsuario actor, CambioAtributos cambio, ContextoPeticion contexto) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(cambio, "cambio");
        CambioAtributos validado = ValidacionAtributos.normalizar(cambio);
        if (!validado.hayCambios()) {
            return consulta.leerPropio(actor);
        }
        UUID id = actor.sub();
        escritura.bloquear(id);
        PerfilProyectado antes = consulta.leerPropio(actor);
        escritura.actualizarBasicos(id, validado);
        List<String> modificados = camposModificados(antes, validado);
        if (!modificados.isEmpty()) {
            eventos.atributosActualizados(id, modificados);
            auditoria.atributosActualizadosPorTitular(actor, modificados, contexto);
        }
        return consulta.leerPropio(actor);
    }

    /** Nombres de los campos presentes cuyo valor normalizado difiere del actual. Nunca los valores. */
    private static List<String> camposModificados(PerfilProyectado antes, CambioAtributos cambio) {
        List<String> campos = new ArrayList<>();
        if (cambio.nombres().presente() && !Objects.equals(cambio.nombres().valor(), antes.nombres())) {
            campos.add("nombres");
        }
        if (cambio.apellidos().presente() && !Objects.equals(cambio.apellidos().valor(), antes.apellidos())) {
            campos.add("apellidos");
        }
        if (cambio.celular().presente() && !Objects.equals(cambio.celular().valor(), antes.celular())) {
            campos.add("celular");
        }
        return List.copyOf(campos);
    }
}
