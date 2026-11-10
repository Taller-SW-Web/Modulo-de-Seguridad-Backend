package com.marketplace.auth_service.sesion.adapter.in.web;

import com.marketplace.auth_service.sesion.adapter.in.web.dto.RenovarSesionRequest;
import com.marketplace.auth_service.sesion.adapter.in.web.dto.SesionIniciadaResponse;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.port.in.CifradoAccesoPort;
import com.marketplace.auth_service.sesion.domain.exception.SecretoInvalidoException;

/**
 * request→command y result→response de sesiones; no decide ni consulta nada.
 * El cuerpo se valida aquí (ausente o en blanco): el caso de uso solo recibe
 * secretos bien formados.
 */
public final class SesionWebMapper {

    /** Valor de {@code formatoAcceso} que pide el acceso cifrado (FEAT-16). */
    public static final String FORMATO_CIFRADO = "CIFRADO";

    private SesionWebMapper() {
    }

    public static SecretoRefresco aSecreto(RenovarSesionRequest cuerpo) {
        if (cuerpo == null || cuerpo.refreshToken() == null || cuerpo.refreshToken().isBlank()) {
            throw new SecretoInvalidoException();
        }
        return new SecretoRefresco(cuerpo.refreshToken());
    }

    /**
     * Solo {@code CIFRADO} cambia el formato. Ausente u otro valor entrega el JWS
     * de siempre, para que ningún cliente actual cambie de comportamiento. Si la
     * capacidad no está disponible también se entrega el JWS.
     */
    public static SesionIniciada conFormato(SesionIniciada sesion, String formatoAcceso, CifradoAccesoPort cifrado) {
        if (cifrado == null || !FORMATO_CIFRADO.equals(formatoAcceso)) {
            return sesion;
        }
        return cifrado.cifrar(sesion);
    }

    public static SesionIniciadaResponse aRespuesta(SesionIniciada iniciada) {
        return new SesionIniciadaResponse(iniciada.accessToken(), iniciada.refreshToken(), "Bearer",
                iniciada.expiresIn(),
                new SesionIniciadaResponse.UsuarioSesionResponse(iniciada.usuario().id(),
                        iniciada.usuario().nombreCompleto(), iniciada.usuario().correo(),
                        iniciada.usuario().roles(), iniciada.usuario().permisos()));
    }
}
