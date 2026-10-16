package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.CampoEditable;
import com.marketplace.auth_service.perfil.application.port.out.EscrituraCuentaPerfilPort;
import com.marketplace.auth_service.usuario.application.dto.CampoBasico;
import com.marketplace.auth_service.usuario.application.port.in.CambioBasicos;
import com.marketplace.auth_service.usuario.application.port.in.CuentaEscrituraInterna;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.UUID;

/** Puente a CuentasPort.bloquear y CuentaEscrituraInterna.actualizarBasicos (Eva). */
public final class EscrituraCuentaPerfilAdapter implements EscrituraCuentaPerfilPort {
    private final CuentasPort cuentas;
    private final CuentaEscrituraInterna escritura;

    public EscrituraCuentaPerfilAdapter(CuentasPort cuentas, CuentaEscrituraInterna escritura) {
        this.cuentas = cuentas;
        this.escritura = escritura;
    }

    @Override
    public void bloquear(UUID usuarioId) {
        cuentas.bloquear(usuarioId);
    }

    @Override
    public void actualizarBasicos(UUID usuarioId, CambioAtributos cambio) {
        escritura.actualizarBasicos(usuarioId, new CambioBasicos(
                traducir(cambio.nombres()), traducir(cambio.apellidos()), traducir(cambio.celular())));
    }

    private static CampoBasico<String> traducir(CampoEditable<String> campo) {
        return campo.presente() ? CampoBasico.presente(campo.valor()) : CampoBasico.omitido();
    }
}
