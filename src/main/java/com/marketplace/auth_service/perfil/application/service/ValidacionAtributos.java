package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.CampoEditable;
import com.marketplace.auth_service.perfil.domain.model.AtributoInvalidoException;
import java.util.regex.Pattern;

/** Valida y normaliza (strip) los campos presentes antes de escribir. Reglas de AtributosUsuario. */
final class ValidacionAtributos {
    private static final int MAX_NOMBRE = 100;
    private static final Pattern CELULAR = Pattern.compile("^\\+51[0-9]{9}$");

    private ValidacionAtributos() {}

    static CambioAtributos normalizar(CambioAtributos cambio) {
        return new CambioAtributos(
                nombre("nombres", cambio.nombres()),
                nombre("apellidos", cambio.apellidos()),
                celular(cambio.celular()));
    }

    private static CampoEditable<String> nombre(String campo, CampoEditable<String> original) {
        if (!original.presente()) {
            return original;
        }
        if (original.valor() == null) {
            throw new AtributoInvalidoException(campo, "no admite null");
        }
        String recortado = original.valor().strip();
        if (recortado.isEmpty()) {
            throw new AtributoInvalidoException(campo, "no puede estar vacío");
        }
        if (recortado.length() > MAX_NOMBRE) {
            throw new AtributoInvalidoException(campo, "máximo " + MAX_NOMBRE + " caracteres");
        }
        return CampoEditable.presente(recortado);
    }

    private static CampoEditable<String> celular(CampoEditable<String> original) {
        if (!original.presente()) {
            return original;
        }
        // El contrato no permite null en celular; se confirma con Sergio (punto 2 de la auditoría).
        if (original.valor() == null) {
            throw new AtributoInvalidoException("celular", "no admite null");
        }
        String recortado = original.valor().strip();
        if (!CELULAR.matcher(recortado).matches()) {
            throw new AtributoInvalidoException("celular", "formato +51 seguido de 9 dígitos");
        }
        return CampoEditable.presente(recortado);
    }
}
