package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.perfil.adapter.in.transaction.PerfilPortTransaccional;
import com.marketplace.auth_service.perfil.adapter.out.capacidad.AuditoriaPerfilAdapter;
import com.marketplace.auth_service.perfil.adapter.out.capacidad.AutorizacionPerfilAdapter;
import com.marketplace.auth_service.perfil.adapter.out.capacidad.CuentasPerfilAdapter;
import com.marketplace.auth_service.perfil.adapter.out.capacidad.EscrituraCuentaPerfilAdapter;
import com.marketplace.auth_service.perfil.adapter.out.capacidad.EventosPerfilAdapter;
import com.marketplace.auth_service.perfil.adapter.out.capacidad.MfaEfectivoPerfilAdapter;
import com.marketplace.auth_service.perfil.adapter.out.capacidad.RolesPerfilAdapter;
import com.marketplace.auth_service.perfil.adapter.out.crypto.ProtectorDocumentoSinClave;
import com.marketplace.auth_service.perfil.adapter.out.persistence.adapter.PerfilesPersistidosAdapter;
import com.marketplace.auth_service.perfil.adapter.out.persistence.repository.PerfilClienteJpaRepository;
import com.marketplace.auth_service.perfil.adapter.out.persistence.repository.PerfilVendedorJpaRepository;
import com.marketplace.auth_service.perfil.application.port.in.ConsultaDocumentoExportPort;
import com.marketplace.auth_service.perfil.application.port.in.PerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.ProtectorDocumento;
import com.marketplace.auth_service.perfil.application.service.ConsultaDocumentoExport;
import com.marketplace.auth_service.perfil.application.service.ConsultarPerfil;
import com.marketplace.auth_service.perfil.application.service.EditarAtributosAdministrativo;
import com.marketplace.auth_service.perfil.application.service.EditarPerfilPropio;
import com.marketplace.auth_service.perfil.application.service.PerfilService;
import com.marketplace.auth_service.perfil.application.service.ProyectorDocumento;
import com.marketplace.auth_service.usuario.application.port.in.CuentaEscrituraInterna;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring de {@code PerfilPort} (EP-08-T002): núcleo de Valery con sus puentes a
 * cuentas y autorización, y la persistencia de perfiles (EP-08-T001). Un único
 * bean exportado, decorado con su transacción.
 *
 * <p>El protector del documento es {@link ProtectorDocumentoSinClave} hasta que
 * exista el adaptador AES con la clave externa; si se define otro bean
 * {@link ProtectorDocumento}, se usa ese.
 */
@Configuration(proxyBeanMethods = false)
public class PerfilConfig {

    @Bean
    PerfilPort perfilPort(CuentasPort cuentas, CuentaEscrituraInterna escritura, AutorizacionPort autorizacion,
                          PerfilClienteJpaRepository clientes, PerfilVendedorJpaRepository vendedores,
                          ObjectProvider<ProtectorDocumento> protector, MensajeriaPort mensajeria,
                          AuditoriaPort auditoria) {
        var cuentasPerfil = new CuentasPerfilAdapter(cuentas);
        var rolesPerfil = new RolesPerfilAdapter(autorizacion);
        var perfiles = new PerfilesPersistidosAdapter(clientes, vendedores);
        var autorizacionPerfil = new AutorizacionPerfilAdapter(autorizacion);
        var escrituraCuenta = new EscrituraCuentaPerfilAdapter(cuentas, escritura);
        var consulta = new ConsultarPerfil(
                cuentasPerfil,
                rolesPerfil,
                new MfaEfectivoPerfilAdapter(cuentas, autorizacion),
                perfiles,
                autorizacionPerfil,
                new ProyectorDocumento(protector.getIfAvailable(ProtectorDocumentoSinClave::new)));
        var edicion = new EditarPerfilPropio(escrituraCuenta, consulta);
        var edicionAdministrativa = new EditarAtributosAdministrativo(autorizacionPerfil, cuentasPerfil, rolesPerfil,
                perfiles, escrituraCuenta, perfiles, new EventosPerfilAdapter(mensajeria),
                new AuditoriaPerfilAdapter(auditoria), consulta, Clock.systemDefaultZone());
        return new PerfilPortTransaccional(new PerfilService(consulta, edicion, edicionAdministrativa));
    }

    /** Capacidad exportada para SPEC-18 (FEAT-06): documento enmascarado o en claro por scope. */
    @Bean
    ConsultaDocumentoExportPort consultaDocumentoExportPort(PerfilClienteJpaRepository clientes,
                                                             PerfilVendedorJpaRepository vendedores,
                                                             ObjectProvider<ProtectorDocumento> protector) {
        return new ConsultaDocumentoExport(new PerfilesPersistidosAdapter(clientes, vendedores),
                protector.getIfAvailable(ProtectorDocumentoSinClave::new));
    }
}
