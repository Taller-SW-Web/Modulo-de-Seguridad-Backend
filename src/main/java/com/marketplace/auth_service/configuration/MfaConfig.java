package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.mfa.adapter.in.transaction.OtpLoginTransaccional;
import com.marketplace.auth_service.mfa.adapter.out.capacidad.HechosMfaAdapter;
import com.marketplace.auth_service.mfa.adapter.out.entrega.EntregaOtpNoDisponible;
import com.marketplace.auth_service.mfa.adapter.out.entrega.ReceptorSmsPruebas;
import com.marketplace.auth_service.mfa.adapter.out.capacidad.EntregaOtpOutbox;
import com.marketplace.auth_service.mfa.application.port.in.OtpLoginPort;
import com.marketplace.auth_service.mfa.application.port.out.EntregaOtpPort;
import com.marketplace.auth_service.mfa.application.service.OtpLoginService;
import com.marketplace.auth_service.mfa.application.service.ValidadorDesafioLogin;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.mfa.adapter.in.transaction.MfaTransaccional;
import com.marketplace.auth_service.mfa.adapter.out.capacidad.*;
import com.marketplace.auth_service.mfa.application.port.in.MfaPort;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.mfa.application.service.MfaService;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Un único puerto productivo; el núcleo nunca se publica sin su decorador. */
@Configuration(proxyBeanMethods = false)
public class MfaConfig {
    @Bean CuentasMfaPort cuentasMfaPort(CuentasPort cuentas) { return new CuentasMfaAdapter(cuentas); }
    @Bean RolesMfaPort rolesMfaPort(AutorizacionPort roles) { return new RolesMfaAdapter(roles); }
    @Bean RevisionCredencialMfaPort revisionCredencialMfaPort(CredencialesPort credenciales) {
        return new RevisionCredencialMfaAdapter(credenciales);
    }
    @Bean MfaPort mfaPort(CuentasMfaPort cuentas, RolesMfaPort roles, RevisionCredencialMfaPort credenciales,
                         PersistenciaMfaPort persistencia, CriptografiaMfaPort criptografia,
                         ObjectProvider<Clock> reloj) {
        return new MfaTransaccional(new MfaService(cuentas, roles, credenciales, persistencia, criptografia,
                reloj.getIfAvailable(Clock::systemUTC), UUID::randomUUID));
    }

    /** EMAIL durable; SMS únicamente mediante receptor del perfil explícito de pruebas. */
    @Bean EntregaOtpPort entregaOtpPort(ObjectProvider<MensajeriaPort> mensajeria, ObjectProvider<ReceptorSmsPruebas> receptor) {
        MensajeriaPort durable = mensajeria.getIfAvailable();
        if (durable == null) return new EntregaOtpNoDisponible();
        ReceptorSmsPruebas simulado = receptor.getIfAvailable();
        EntregaOtpPort sms = simulado == null ? new EntregaOtpNoDisponible() : (canal, destino, codigo) -> {
            if (canal != com.marketplace.auth_service.mfa.domain.model.CanalOtp.SMS)
                throw new com.marketplace.auth_service.mfa.domain.exception.EntregaOtpNoDisponibleException();
            simulado.recibir(destino, codigo);
        };
        return new EntregaOtpOutbox(durable, sms);
    }

    @Bean OtpLoginPort otpLoginPort(CuentasMfaPort cuentas, RolesMfaPort roles, RevisionCredencialMfaPort credenciales,
                                    PersistenciaMfaPort persistencia, CriptografiaMfaPort criptografia,
                                    EntregaOtpPort entrega, ObjectProvider<AuditoriaPort> auditoria,
                                    ObjectProvider<Clock> reloj) {
        AuditoriaPort registro = auditoria.getIfAvailable();
        Clock clock = reloj.getIfAvailable(Clock::systemUTC);
        return new OtpLoginTransaccional(new OtpLoginService(
                new ValidadorDesafioLogin(persistencia, criptografia, cuentas, roles, credenciales, clock),
                cuentas, persistencia, criptografia, entrega, clock, UUID::randomUUID,
                registro == null ? usuario -> { } : new HechosMfaAdapter(registro)));
    }
}
