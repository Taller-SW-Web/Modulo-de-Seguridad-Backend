package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.out.EscrituraCuentaPerfilPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.Objects;
import java.util.UUID;

/**
 * Edición propia de nombres/apellidos/celular. Orden: validar todo → bloquear cuenta → escribir
 * → releer. Un inválido no escribe nada. Eventos/auditoría llegan con EP-08-T004.
 * Debe ejecutarse dentro de una transacción (la abre el decorador de adapter.in.transaction).
 */
public final class EditarPerfilPropio {
    private final EscrituraCuentaPerfilPort escritura;
    private final ConsultarPerfil consulta;

    public EditarPerfilPropio(EscrituraCuentaPerfilPort escritura, ConsultarPerfil consulta) {
        this.escritura = Objects.requireNonNull(escritura, "escritura");
        this.consulta = Objects.requireNonNull(consulta, "consulta");
    }

    public PerfilProyectado actualizarPropio(ActorUsuario actor, CambioAtributos cambio) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(cambio, "cambio");
        CambioAtributos validado = ValidacionAtributos.normalizar(cambio);
        if (!validado.hayCambios()) {
            return consulta.leerPropio(actor);
        }
        UUID id = actor.sub();
        escritura.bloquear(id);
        escritura.actualizarBasicos(id, validado);
        return consulta.leerPropio(actor);
    }
}
