package com.marketplace.auth_service.identidad.application.port.in;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ClaimsServicio;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.Descubrimiento;
import com.marketplace.auth_service.identidad.application.dto.InspeccionAcceso;
import com.marketplace.auth_service.identidad.application.dto.ServicioValidado;
import com.marketplace.auth_service.identidad.application.dto.TokenServicioFirmado;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;

import java.util.List;

/**
 * Capacidad exportada de firma (ACUERDOS-INTERNOS §2). No autentica contraseñas,
 * no calcula roles ni abre familias: firma lo que recibe, valida lo que firmó y
 * publica su parte pública.
 *
 * <p>Todas las operaciones lanzan {@link FirmaNoDisponibleException} si la
 * clave o el issuer del entorno faltan o son inválidos.
 */
public interface FirmaPort {

    /** Firma un acceso RS256 de 900 s (TEC-IDENTIDAD-T001). */
    AccesoFirmado firmar(ClaimsAcceso claims);

    /**
     * Valida un acceso de persona (TEC-IDENTIDAD-T002): firma RS256 con la clave
     * local (sin HTTP a nuestro propio JWKS), {@code kid}, issuer exacto, tiempos
     * sin tolerancia ({@code exp <= ahora} es vencido; {@code iat} obligatorio y no
     * futuro; {@code nbf} no futuro si viene) y perfil {@code tipo=acceso} con
     * {@code exp - iat = 900 s}, {@code email}, {@code sub}/{@code jti} UUID y
     * {@code roles}/{@code permisos} como arrays.
     *
     * @throws TokenInvalidoException    → {@code 401 TOKEN_INVALIDO}
     * @throws TokenNoAplicableException token de servicio válido → {@code 403 TOKEN_NO_APLICABLE}
     */
    AccesoValidado validar(String token);

    /** Firma un token de servicio RS256 de 3600 s (SPEC-17 RF-17.4/17.5/17.12/17.13). */
    TokenServicioFirmado firmarServicio(ClaimsServicio claims);

    /**
     * Valida un token de servicio dirigido a esta API: firma, issuer, mismos
     * tiempos sin tolerancia, {@code tipo=servicio}, {@code exp - iat = 3600 s},
     * {@code sub} no vacío, {@code jti} UUID y {@code aud} con {@code api-seguridad}.
     * Devuelve todos los scopes del claim; cada operación exige el suyo.
     *
     * @throws TokenInvalidoException    → {@code 401 TOKEN_INVALIDO}
     * @throws TokenNoAplicableException acceso de persona válido donde se exige servicio
     */
    ServicioValidado validarServicio(String token);

    /**
     * Inspecciona el acceso de persona que un módulo pide introspeccionar
     * (RF-17.6): distingue vencido de inválido sin lanzar. No consulta sesión ni
     * cuenta: eso lo decide la introspección.
     */
    InspeccionAcceso inspeccionar(String token);

    /** Claves públicas vigentes para el JWKS (TEC-IDENTIDAD-T003, RF-17.1, RF-17.3). */
    List<ClavePublica> jwks();

    /** Documento de descubrimiento: issuer idéntico al {@code iss} emitido (RF-17.2, RF-17.14). */
    Descubrimiento descubrimiento();
}
