package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.sesion.adapter.in.transaction.SesionTransaccional;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.EfectosCierreAdapter;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteCuentasSesion;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteFirmaAcceso;
import com.marketplace.auth_service.sesion.adapter.out.crypto.SesionCryptoAdapter;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.*;
import com.marketplace.auth_service.sesion.application.service.SesionService;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

/** JPA ya se registra por @Repository: no crear un segundo bean del puerto. */
@Configuration(proxyBeanMethods = false)
public class SesionConfig {
    @Bean CriptografiaSesionPort criptografiaSesionPort() { return new SesionCryptoAdapter(); }
    @Bean FirmadorAccesoPort firmadorAccesoPort(FirmaPort firma) { return new PuenteFirmaAcceso(firma); }
    @Bean PuenteCuentasSesion puenteCuentasSesion(CuentasPort cuentas, AutorizacionPort autorizacion,
            ObjectProvider<ProteccionPort> proteccion, ObjectProvider<Clock> reloj) {
        return new PuenteCuentasSesion(cuentas, autorizacion, proteccion.getIfAvailable(),
                reloj.getIfAvailable(Clock::systemUTC));
    }
    @Bean EfectosCierrePort efectosCierrePort(AuditoriaPort auditoria) {
        return new EfectosCierreAdapter(auditoria);
    }
    @Bean SesionesPort sesionesPort(PersistenciaSesionPort persistencia, CriptografiaSesionPort criptografia,
            FirmadorAccesoPort firmador, ProveedorIdentidadPort identidad, BloqueoCuentaSesionPort cuentas,
            EfectosCierrePort efectosCierre, ObjectProvider<Clock> clock) {
        return new SesionTransaccional(new SesionService(persistencia, criptografia, firmador, identidad,
                efectosCierre, clock.getIfAvailable(Clock::systemUTC)), persistencia, criptografia, cuentas);
    }

}
