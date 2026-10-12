package com.marketplace.auth_service.identidad.application.port.in;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.Descubrimiento;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;

import java.util.List;

/**
 * Capacidad exportada de firma (ACUERDOS-INTERNOS §2). No autentica contraseñas,
 * no calcula roles ni abre familias: firma lo que recibe y publica su parte pública.
 *
 * <p>Todas las operaciones lanzan {@link FirmaNoDisponibleException} si la
 * clave o el issuer del entorno faltan o son inválidos.
 */
public interface FirmaPort {

    /** Firma un acceso RS256 de 900 s (TEC-IDENTIDAD-T001). */
    AccesoFirmado firmar(ClaimsAcceso claims);

    /** Claves públicas vigentes para el JWKS (TEC-IDENTIDAD-T003, RF-17.1). */
    List<ClavePublica> jwks();

    /** Documento de descubrimiento: issuer idéntico al {@code iss} emitido (RF-17.2, RF-17.14). */
    Descubrimiento descubrimiento();
}
