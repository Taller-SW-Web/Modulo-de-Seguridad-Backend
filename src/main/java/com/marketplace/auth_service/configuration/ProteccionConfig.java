package com.marketplace.auth_service.configuration;
import com.marketplace.auth_service.proteccion.adapter.in.transaction.ProteccionTransaccional;
import com.marketplace.auth_service.proteccion.adapter.out.capacidad.PuenteCuentasProteccion;
import com.marketplace.auth_service.proteccion.adapter.out.crypto.HashEnlaceProteccionJca;
import com.marketplace.auth_service.proteccion.application.dto.*;
import com.marketplace.auth_service.proteccion.application.port.in.ConsultaBloqueosPort;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import com.marketplace.auth_service.proteccion.application.service.ConsultaBloqueosService;
import com.marketplace.auth_service.proteccion.application.port.out.*;
import com.marketplace.auth_service.proteccion.application.service.ProteccionService;
import com.marketplace.auth_service.proteccion.domain.exception.ProteccionNoDisponibleException;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.usuario.application.port.in.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import java.time.*;
import java.util.*;

/** No registra tokens ni efectos ficticios. Ausencia de proveedores implica excepción
 * y rollback; los adaptadores reales se conectarán al integrarse sus capacidades. */
@Configuration(proxyBeanMethods=false)
public class ProteccionConfig {
    @Bean CuentasProteccionPort cuentasProteccion(CuentasPort cuentas, CuentaEscrituraInterna escritura, AutorizacionPort roles) {
        return new PuenteCuentasProteccion(cuentas,escritura,roles);
    }
    @Bean HashEnlaceProteccionPort hashEnlaceProteccion() { return new HashEnlaceProteccionJca(); }
    /** Lectura de bloqueos vigentes para la vista administrativa (RF-15.6); sin locks ni efectos. */
    @Bean ConsultaBloqueosPort consultaBloqueosPort(PersistenciaProteccionPort persistencia, ObjectProvider<Clock> reloj) {
        return new ConsultaBloqueosService(persistencia, reloj.getIfAvailable(Clock::systemUTC));
    }
    @Bean ProteccionPort proteccionPort(PersistenciaProteccionPort persistencia, HuellaProteccionPort huellas,
            CuentasProteccionPort cuentas, HashEnlaceProteccionPort hash,
            ObjectProvider<TokensProteccionPort> tokens, ObjectProvider<EfectosProteccionPort> efectos,
            ObjectProvider<Clock> reloj) {
        // Resolutores locales, NO beans de proveedores. Nunca devuelven éxito si faltan.
        TokensProteccionPort requerido=new TokensProteccionPort() {
            public Optional<TokenDesbloqueo> localizar(String h) { return exigir(tokens,"TokensUnUsoPort #20").localizar(h); }
            public EnlaceEmitido emitir(UUID u, UUID r, Duration d) { return exigir(tokens,"TokensUnUsoPort #20").emitir(u,r,d); }
            public boolean consumir(UUID id, UUID u, UUID r, Instant a) { return exigir(tokens,"TokensUnUsoPort #20").consumir(id,u,r,a); }
            public void invalidarPrevios(UUID u) { exigir(tokens,"TokensUnUsoPort #20").invalidarPrevios(u); }
        };
        EfectosProteccionPort criticos=new EfectosProteccionPort() {
            public void bloqueada(CuentaSnapshot c,UUID r,Instant h,EnlaceEmitido e,ContextoPeticion p,Instant a) {
                exigir(efectos,"auditoría/evento/correo durable Christian").bloqueada(c,r,h,e,p,a);
            }
            public void desbloqueada(CuentaSnapshot c,UUID r,ContextoPeticion p,Instant a) {
                exigir(efectos,"auditoría/evento Christian").desbloqueada(c,r,p,a);
            }
        };
        return new ProteccionTransaccional(new ProteccionService(persistencia,huellas,cuentas,requerido,criticos,
                hash,reloj.getIfAvailable(Clock::systemUTC)));
    }
    private static <T> T exigir(ObjectProvider<T> proveedor,String nombre) {
        T real=proveedor.getIfAvailable();
        if (real==null) throw new ProteccionNoDisponibleException(nombre);
        return real;
    }
}
