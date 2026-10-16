package com.marketplace.auth_service.sesion.application.port.out;

import com.marketplace.auth_service.sesion.domain.model.AccesoSesion;
import com.marketplace.auth_service.sesion.domain.model.SesionFamilia;
import com.marketplace.auth_service.sesion.domain.model.TokenRefresco;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia propia de sesión (EP-02-T002). Guardados y locks requieren la
 * transacción del coordinador y el lock previo de usuario (orden: cuenta, raíz
 * de familia, filas propias). La cuenta solo se consulta mediante Eva; aquí
 * usuario_id viaja como UUID escalar, sin repositorio ni entidad ajenos.
 */
public interface PersistenciaSesionPort {
    void guardarFamilia(SesionFamilia familia);
    Optional<SesionFamilia> bloquearFamilia(UUID familiaId);
    List<SesionFamilia> listarFamiliasDeUsuario(UUID usuarioId);
    void guardarToken(TokenRefresco token);
    Optional<TokenRefresco> buscarTokenPorHash(String hashToken);
    List<TokenRefresco> listarTokensDeFamilia(UUID familiaId);
    /** Inserta un acceso nuevo; rechaza jti repetido, nunca lo reemplaza. */
    void guardarAcceso(AccesoSesion acceso);
    Optional<AccesoSesion> buscarAccesoPorJti(UUID jti);
}
