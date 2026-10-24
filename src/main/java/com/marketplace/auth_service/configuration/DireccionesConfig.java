package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.direccion.adapter.in.transaction.DireccionesPortTransaccional;
import com.marketplace.auth_service.direccion.adapter.out.capacidad.CuentaDireccionesAdapter;
import com.marketplace.auth_service.direccion.adapter.out.capacidad.MensajeriaDireccionesAdapter;
import com.marketplace.auth_service.direccion.application.port.in.ConsultaDireccionesPort;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort;
import com.marketplace.auth_service.direccion.application.port.out.DireccionesPersistidasPort;
import com.marketplace.auth_service.direccion.application.service.ConsultaDireccionesService;
import com.marketplace.auth_service.direccion.application.service.DireccionesService;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring de direcciones: {@code DireccionesPort} (FEAT-04-T1, núcleo + puentes, decorado con su transacción) y
 * {@code ConsultaDireccionesPort} (FEAT-04-T2, lectura exportada).
 */
@Configuration(proxyBeanMethods = false)
public class DireccionesConfig {
    @Bean ConsultaDireccionesPort consultaDireccionesPort(DireccionesPersistidasPort persistencia) {
        return new ConsultaDireccionesService(persistencia);
    }

    @Bean DireccionesPort direccionesPort(DireccionesPersistidasPort persistencia, CuentasPort cuentas, MensajeriaPort mensajeria) {
        return new DireccionesPortTransaccional(new DireccionesService(persistencia,
                new CuentaDireccionesAdapter(cuentas), new MensajeriaDireccionesAdapter(mensajeria)));
    }
}
