package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import com.marketplace.auth_service.direccion.adapter.in.transaction.DireccionesPortTransaccional;
import com.marketplace.auth_service.direccion.adapter.out.capacidad.CuentaDireccionesAdapter;
import com.marketplace.auth_service.direccion.adapter.out.capacidad.OutboxDireccionesAdapter;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort;
import com.marketplace.auth_service.direccion.application.port.out.DireccionesPersistidasPort;
import com.marketplace.auth_service.direccion.application.service.DireccionesService;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring de {@code DireccionesPort} (FEAT-04-T1): núcleo + puentes, decorado con su transacción. */
@Configuration(proxyBeanMethods = false)
public class DireccionesConfig {
    @Bean DireccionesPort direccionesPort(DireccionesPersistidasPort persistencia, CuentasPort cuentas, OutboxPort outbox) {
        return new DireccionesPortTransaccional(new DireccionesService(persistencia,
                new CuentaDireccionesAdapter(cuentas), new OutboxDireccionesAdapter(outbox)));
    }
}
