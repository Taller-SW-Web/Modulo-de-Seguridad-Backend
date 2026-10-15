package com.marketplace.auth_service.perfil.application.port.in;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.UUID;

/**
 * Capacidad exportada de perfil (ACUERDOS-INTERNOS §2). El actor viene de un acceso ya validado.
 * Nota: el cambio usa CambioAtributos propio (la regla de consumo entre capacidades impide
 * importar CambioBasicos de usuario); el puente lo traduce al contrato de Eva.
 */
public interface PerfilPort {
    /** Perfil privado del titular (sub del actor). No acepta otro id. */
    PerfilProyectado leerPropio(ActorUsuario actor);

    /** Proyección para titular o quien tenga usuario.ver (revalidado). */
    PerfilProyectado proyectarPara(ActorUsuario actor, UUID usuarioId);

    /** Edita solo nombres/apellidos/celular propios; los omitidos se conservan. Devuelve el perfil actualizado. */
    PerfilProyectado actualizarPropio(ActorUsuario actor, CambioAtributos cambio);
}
