package com.marketplace.auth_service.perfil.adapter.in.web;

import com.marketplace.auth_service.perfil.application.dto.CambioAdministrativo;
import com.marketplace.auth_service.perfil.application.dto.CampoEditable;
import com.marketplace.auth_service.perfil.domain.model.AtributoInvalidoException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Cuerpo JSON de {@code PATCH /usuarios/{id}/atributos} administrativo → {@link CambioAdministrativo}
 * (FEAT-04-T6). Misma presencia explícita que la edición propia: clave ausente = omitido, null = presente
 * con null. Solo pasan los campos de {@code AtributosUsuario}; el resto (correo, roles, estado…) se ignora.
 * Del documento solo se anota que vino, sin copiar su valor.
 */
final class AtributosAdministrativosWebMapper {

    private AtributosAdministrativosWebMapper() {
    }

    static CambioAdministrativo aCambio(Map<String, Object> cuerpo) {
        return new CambioAdministrativo(
                texto("nombres", cuerpo),
                texto("apellidos", cuerpo),
                texto("celular", cuerpo),
                fecha("fechaNacimiento", cuerpo),
                texto("codigoVendedor", cuerpo),
                texto("tienda", cuerpo),
                fecha("fechaIngreso", cuerpo),
                cuerpo.containsKey("tipoDocumento") || cuerpo.containsKey("numeroDocumento"));
    }

    private static CampoEditable<String> texto(String campo, Map<String, Object> cuerpo) {
        if (!cuerpo.containsKey(campo)) {
            return CampoEditable.omitido();
        }
        Object valor = cuerpo.get(campo);
        if (valor == null) {
            return CampoEditable.presente(null);
        }
        if (valor instanceof String texto) {
            return CampoEditable.presente(texto);
        }
        throw new AtributoInvalidoException(campo, "debe ser texto");
    }

    private static CampoEditable<LocalDate> fecha(String campo, Map<String, Object> cuerpo) {
        if (!cuerpo.containsKey(campo)) {
            return CampoEditable.omitido();
        }
        Object valor = cuerpo.get(campo);
        if (valor == null) {
            return CampoEditable.presente(null);
        }
        if (valor instanceof String texto) {
            try {
                return CampoEditable.presente(LocalDate.parse(texto.strip()));
            } catch (DateTimeParseException e) {
                throw new AtributoInvalidoException(campo, "fecha inválida (AAAA-MM-DD)");
            }
        }
        throw new AtributoInvalidoException(campo, "fecha inválida (AAAA-MM-DD)");
    }
}
