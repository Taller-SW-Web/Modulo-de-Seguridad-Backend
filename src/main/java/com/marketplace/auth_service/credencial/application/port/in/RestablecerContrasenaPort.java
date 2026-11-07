package com.marketplace.auth_service.credencial.application.port.in;

import com.marketplace.auth_service.credencial.application.dto.ResultadoRestablecimiento;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;

/** Restablece una contraseña mediante un enlace RECUPERACION de un solo uso. */
public interface RestablecerContrasenaPort {
    ResultadoRestablecimiento restablecer(SecretoEfimero token, SecretoEfimero nuevaContrasena,
                                           com.marketplace.auth_service.shared.domain.ContextoPeticion contexto);
}
