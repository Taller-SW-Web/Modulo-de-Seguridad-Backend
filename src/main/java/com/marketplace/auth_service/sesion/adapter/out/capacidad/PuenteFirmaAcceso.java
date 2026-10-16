package com.marketplace.auth_service.sesion.adapter.out.capacidad;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.sesion.application.dto.DatosAcceso;
import com.marketplace.auth_service.sesion.application.dto.TokenAccesoFirmado;
import com.marketplace.auth_service.sesion.application.port.out.FirmadorAccesoPort;

import java.util.Objects;

/**
 * Puente hacia la firma de Sergio: implementa el puerto de salida propio y
 * delega únicamente al {@code FirmaPort} exportado y sus DTO. No importa
 * dominio, servicios internos ni adaptadores de identidad.
 */
public class PuenteFirmaAcceso implements FirmadorAccesoPort {

    private final FirmaPort firma;

    public PuenteFirmaAcceso(FirmaPort firma) {
        this.firma = Objects.requireNonNull(firma, "firma");
    }

    @Override
    public TokenAccesoFirmado firmar(DatosAcceso datos) {
        AccesoFirmado firmado = firma.firmar(
                new ClaimsAcceso(datos.usuarioId(), datos.email(), datos.roles(), datos.permisos()));
        return new TokenAccesoFirmado(firmado.token(), firmado.jti(), firmado.emitidoEn(),
                firmado.expiraEn());
    }
}
