package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.out.EfectosPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.EscrituraCuentaPerfilPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Edición propia de nombres/apellidos/celular. Orden: validar todo → bloquear cuenta → escribir
 * → releer. Un inválido no escribe nada. Si algún campo cambió de verdad, deja el evento
 * {@code usuario.atributos_actualizados} y la auditoría por {@link EfectosPerfilPort} (solo nombres).
 * Debe ejecutarse dentro de una transacción (la abre el decorador de adapter.in.transaction).
 */
public final class EditarPerfilPropio {
    private final EscrituraCuentaPerfilPort escritura;
    private final ConsultarPerfil consulta;
    private final EfectosPerfilPort efectos;

    /** Sin efectos externos (pruebas del núcleo). La producción usa el constructor con efectos. */
    public EditarPerfilPropio(EscrituraCuentaPerfilPort escritura, ConsultarPerfil consulta) {
        this(escritura, consulta, (actor, usuario, campos) -> { });
    }

    public EditarPerfilPropio(EscrituraCuentaPerfilPort escritura, ConsultarPerfil consulta,
                              EfectosPerfilPort efectos) {
        this.escritura = Objects.requireNonNull(escritura, "escritura");
        this.consulta = Objects.requireNonNull(consulta, "consulta");
        this.efectos = Objects.requireNonNull(efectos, "efectos");
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
        PerfilProyectado antes = consulta.leerPropio(actor);
        escritura.actualizarBasicos(id, validado);
        List<String> modificados = camposModificados(antes, validado);
        if (!modificados.isEmpty()) {
            efectos.atributosActualizados(actor.sub(), id, modificados);
        }
        return consulta.leerPropio(actor);
    }

    /** Nombres de los campos presentes cuyo valor realmente difiere del actual. Nunca los valores. */
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
