package com.marketplace.auth_service.proteccion.application.port.out;

import com.marketplace.auth_service.proteccion.domain.model.*;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia propia, no API de integración. Mutaciones y locks requieren
 * transacción y orden guard→cuenta (Eva)→filas propias del coordinador de T002.
 * La ausencia de fila no es un lock: crear racha exige primero el lock de cuenta.
 */
public interface PersistenciaProteccionPort {
    Optional<RachaFallo> buscarRacha(UUID usuarioId);
    Optional<RachaFallo> bloquearRacha(UUID usuarioId);
    void crearRacha(RachaFallo racha);
    void reiniciarRacha(RachaFallo nuevaRacha);
    boolean insertarHuellaSiNueva(HuellaFallo huella);
    long contarHuellas(UUID usuarioId, UUID rachaId);
    void registrarIntento(IntentoLogin intento);
    Optional<Bloqueo> buscarBloqueo(UUID usuarioId);
    Optional<Bloqueo> bloquearBloqueo(UUID usuarioId);
    void guardarBloqueo(Bloqueo bloqueo);
    boolean eliminarBloqueo(UUID usuarioId, UUID revisionEsperada);
}
