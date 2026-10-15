package com.marketplace.auth_service.sesion.adapter.in.web;

import com.marketplace.auth_service.sesion.adapter.in.web.dto.DesafioMfaResponse;
import com.marketplace.auth_service.sesion.adapter.in.web.dto.IniciarSesionRequest;
import com.marketplace.auth_service.sesion.application.dto.CredencialesLogin;
import com.marketplace.auth_service.sesion.application.dto.DesafioMfaRespuesta;
import com.marketplace.auth_service.sesion.application.dto.SecretoEfimero;
import com.marketplace.auth_service.sesion.domain.exception.DatoObligatorioException;

/** request→command de login; la respuesta de sesión reutiliza `SesionWebMapper`. */
public final class LoginWebMapper {

    private LoginWebMapper() {
    }

    public static CredencialesLogin aComando(IniciarSesionRequest cuerpo) {
        if (cuerpo == null || cuerpo.correo() == null || cuerpo.correo().isBlank()) {
            throw new DatoObligatorioException("correo");
        }
        if (cuerpo.contrasena() == null || cuerpo.contrasena().isBlank()) {
            throw new DatoObligatorioException("contrasena");
        }
        return new CredencialesLogin(cuerpo.correo(), new SecretoEfimero(cuerpo.contrasena()));
    }

    public static DesafioMfaResponse aRespuesta(DesafioMfaRespuesta desafio) {
        return new DesafioMfaResponse(true, desafio.challengeToken(), desafio.canal(), desafio.expiraEn());
    }
}
