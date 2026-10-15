package com.marketplace.auth_service.proteccion.adapter.out.capacidad;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.usuario.application.port.in.*;
import com.marketplace.auth_service.proteccion.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.proteccion.application.port.out.CuentasProteccionPort;
import java.util.*;
public final class PuenteCuentasProteccion implements CuentasProteccionPort {
    private final CuentasPort cuentas;
    private final CuentaEscrituraInterna escritura;
    private final AutorizacionPort autorizacion;
    public PuenteCuentasProteccion(CuentasPort cuentas, CuentaEscrituraInterna escritura, AutorizacionPort autorizacion) {
        this.cuentas=cuentas; this.escritura=escritura; this.autorizacion=autorizacion;
    }
    public static CuentaSnapshot proyectar(com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot c) {
        return new CuentaSnapshot(c.id(), c.correo(), c.estadoBase(), c.bloqueosSeguidos(), c.intentosFallidosConsecutivos());
    }
    @Override public Optional<CuentaSnapshot> leer(UUID id) { return cuentas.leer(id).map(PuenteCuentasProteccion::proyectar); }
    @Override public CuentaSnapshot bloquear(UUID id) { return proyectar(cuentas.bloquear(id)); }
    @Override public void bloquearCatalogoAdministrador() { autorizacion.bloquearCatalogoAdministrador(); }
    @Override public void guardarContadores(UUID id, int bloqueos, int intentos) {
        escritura.guardarContadores(id, new ContadoresProteccion(bloqueos,intentos));
    }
}
