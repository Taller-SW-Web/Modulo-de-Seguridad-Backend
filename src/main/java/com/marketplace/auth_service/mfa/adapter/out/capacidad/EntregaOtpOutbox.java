package com.marketplace.auth_service.mfa.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.mfa.application.port.out.EntregaOtpPort;
import com.marketplace.auth_service.mfa.domain.model.CanalOtp;
import java.util.Objects;
import java.util.UUID;

/** EMAIL se cifra y persiste mediante el proveedor real dentro del commit del OTP.
 * El transporte/reintento pertenece al worker existente; SMS conserva su adaptador previo. */
public final class EntregaOtpOutbox implements EntregaOtpPort {
    private final MensajeriaPort mensajeria;
    private final EntregaOtpPort sms;
    public EntregaOtpOutbox(MensajeriaPort mensajeria, EntregaOtpPort sms) {
        this.mensajeria = Objects.requireNonNull(mensajeria);
        this.sms = Objects.requireNonNull(sms);
    }
    @Override public void programar(CanalOtp canal, String destino, String codigo) {
        if (canal == CanalOtp.SMS) {
            sms.programar(canal, destino, codigo);
            return;
        }
        mensajeria.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "otp", destino,
                "Tu código para iniciar sesión es " + codigo + ".\n\n"
                + "Solo es válido mientras tu desafío siga vigente. Si solicitaste otro, utiliza el más reciente.\n"));
    }
}
