package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.CambioAdministrativo;
import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.CampoEditable;
import com.marketplace.auth_service.perfil.domain.model.AtributoInvalidoException;
import java.time.LocalDate;

/**
 * Reglas de AtributosUsuario para la edición administrativa. Los básicos usan las mismas reglas
 * que la edición propia; los atributos de rol admiten null para dejarlos sin registrar.
 */
final class ValidacionAtributosAdministrativos {
    private static final int MAX_CODIGO_VENDEDOR = 50;
    private static final int MAX_TIENDA = 100;

    private ValidacionAtributosAdministrativos() {}

    static CambioAdministrativo normalizar(CambioAdministrativo cambio, LocalDate hoy) {
        CambioAtributos basicos = ValidacionAtributos.normalizar(cambio.basicos());
        return new CambioAdministrativo(
                basicos.nombres(),
                basicos.apellidos(),
                basicos.celular(),
                fechaNacimiento(cambio.fechaNacimiento(), hoy),
                texto("codigoVendedor", cambio.codigoVendedor(), MAX_CODIGO_VENDEDOR),
                texto("tienda", cambio.tienda(), MAX_TIENDA),
                cambio.fechaIngreso(),
                cambio.documentoPresente());
    }

    private static CampoEditable<String> texto(String campo, CampoEditable<String> original, int maximo) {
        if (!original.presente() || original.valor() == null) {
            return original;
        }
        String recortado = original.valor().strip();
        if (recortado.isEmpty()) {
            throw new AtributoInvalidoException(campo, "no puede estar vacío");
        }
        if (recortado.length() > maximo) {
            throw new AtributoInvalidoException(campo, "máximo " + maximo + " caracteres");
        }
        return CampoEditable.presente(recortado);
    }

    private static CampoEditable<LocalDate> fechaNacimiento(CampoEditable<LocalDate> original, LocalDate hoy) {
        if (!original.presente() || original.valor() == null) {
            return original;
        }
        if (!original.valor().isBefore(hoy)) {
            throw new AtributoInvalidoException("fechaNacimiento", "debe ser anterior a hoy");
        }
        return original;
    }
}
