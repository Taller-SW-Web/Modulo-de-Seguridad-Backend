package com.marketplace.auth_service.identidad.application.port.in;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;

/**
 * Capacidad exportada de firma (ACUERDOS-INTERNOS §2). No autentica contraseñas,
 * no calcula roles ni abre familias: firma lo que recibe.
 *
 * <p>{@code validar}, {@code jwks} y {@code descubrimiento} se añaden en
 * TEC-IDENTIDAD-T002 y T003.
 */
public interface FirmaPort {

    /**
     * Firma un acceso RS256 de 900 s.
     *
     * @throws FirmaNoDisponibleException si falta o es inválida la clave o el issuer
     */
    AccesoFirmado firmar(ClaimsAcceso claims);
}
