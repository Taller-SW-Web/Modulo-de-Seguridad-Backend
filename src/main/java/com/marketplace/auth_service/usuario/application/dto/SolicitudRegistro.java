package com.marketplace.auth_service.usuario.application.dto;

import java.util.Locale;
import java.util.Objects;

/**
 * Datos del registro público (SPEC-01, FEAT-01-T1) ya validados en forma por el
 * adaptador HTTP. El correo se normaliza como en el resto de cuentas; la contraseña
 * se conserva tal cual y nunca aparece en {@link #toString()}.
 *
 * @param canalOrigen nombre del canal (WEB, CHATBOT, RETAIL o MARKETPLACE)
 */
public record SolicitudRegistro(String correo, String nombres, String apellidos, String celular,
                                String canalOrigen, String contrasena) {
    public SolicitudRegistro {
        Objects.requireNonNull(correo, "correo");
        Objects.requireNonNull(nombres, "nombres");
        Objects.requireNonNull(apellidos, "apellidos");
        Objects.requireNonNull(celular, "celular");
        Objects.requireNonNull(canalOrigen, "canalOrigen");
        Objects.requireNonNull(contrasena, "contrasena");
        correo = correo.strip().toLowerCase(Locale.ROOT);
        nombres = nombres.strip();
        apellidos = apellidos.strip();
    }

    @Override
    public String toString() {
        return "SolicitudRegistro[canalOrigen=" + canalOrigen + ", REDACTADO]";
    }
}
