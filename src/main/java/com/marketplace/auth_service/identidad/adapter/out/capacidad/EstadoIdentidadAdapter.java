package com.marketplace.auth_service.identidad.adapter.out.capacidad;

import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.identidad.application.port.out.EstadoIdentidadPort;
import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puente de la introspección hacia sesiones, cuentas y autorización, solo por
 * sus puertos exportados. Un acceso desconocido o una familia revocada llegan
 * como excepción de dominio de sesiones: aquí significan «no vigente». Un fallo
 * técnico (base caída) no se disfraza de revocación y se propaga.
 */
public final class EstadoIdentidadAdapter implements EstadoIdentidadPort {

    private final SesionesPort sesiones;
    private final CuentasPort cuentas;
    private final AutorizacionPort autorizacion;

    public EstadoIdentidadAdapter(SesionesPort sesiones, CuentasPort cuentas, AutorizacionPort autorizacion) {
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.cuentas = Objects.requireNonNull(cuentas, "cuentas");
        this.autorizacion = Objects.requireNonNull(autorizacion, "autorizacion");
    }

    @Override
    public boolean accesoVigente(UUID sub, UUID jti) {
        try {
            sesiones.resolverAcceso(new AccesoVerificado(sub, jti));
            return true;
        } catch (DataAccessException | TransactionException tecnico) {
            throw tecnico;
        } catch (RuntimeException desconocidoORevocado) {
            return false;
        }
    }

    @Override
    public Optional<Cuenta> cuenta(UUID sub) {
        return cuentas.leer(sub).map(c -> new Cuenta(c.estadoEfectivo(), c.correo()));
    }

    @Override
    public Permisos permisos(UUID sub) {
        PermisosResueltos resueltos = autorizacion.resolver(sub);
        return new Permisos(resueltos.codigosRoles(), resueltos.codigosPermisos());
    }
}
