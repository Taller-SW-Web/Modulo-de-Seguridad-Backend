package com.marketplace.auth_service.sesion.adapter.in.web;

import com.marketplace.auth_service.sesion.adapter.in.web.dto.RenovarSesionRequest;
import com.marketplace.auth_service.sesion.adapter.in.web.dto.SesionIniciadaResponse;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.domain.exception.SecretoInvalidoException;

/**
 * request→command y result→response de sesiones; no decide ni consulta nada.
 * El cuerpo se valida aquí (ausente o en blanco): el caso de uso solo recibe
 * secretos bien formados.
 */
public final class SesionWebMapper {

    private SesionWebMapper() {
    }

    public static SecretoRefresco aSecreto(RenovarSesionRequest cuerpo) {
        if (cuerpo == null || cuerpo.refreshToken() == null || cuerpo.refreshToken().isBlank()) {
            throw new SecretoInvalidoException();
        }
        return new SecretoRefresco(cuerpo.refreshToken());
    }

    public static SesionIniciadaResponse aRespuesta(SesionIniciada iniciada) {
        return new SesionIniciadaResponse(iniciada.accessToken(), iniciada.refreshToken(), "Bearer",
                iniciada.expiresIn(),
                new SesionIniciadaResponse.UsuarioSesionResponse(iniciada.usuario().id(),
                        iniciada.usuario().nombreCompleto(), iniciada.usuario().correo(),
                        iniciada.usuario().roles()));
    }
}
