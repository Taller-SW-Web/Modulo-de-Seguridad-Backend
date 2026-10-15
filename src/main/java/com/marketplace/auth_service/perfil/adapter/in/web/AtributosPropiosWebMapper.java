package com.marketplace.auth_service.perfil.adapter.in.web;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.CampoEditable;
import com.marketplace.auth_service.perfil.domain.model.AtributoInvalidoException;
import java.util.Map;
import java.util.Set;

/**
 * Cuerpo JSON de {@code PATCH /usuarios/{id}/atributos} → {@link CambioAtributos}.
 *
 * <p>Presencia explícita: una clave ausente es «omitido» (conserva el dato) y una clave
 * con {@code null} es «presente con null»; el servicio decide si el null es válido.
 * Whitelist: solo {@code nombres}, {@code apellidos} y {@code celular} llegan al
 * servicio. Los atributos del esquema que exigen {@code atributos.editar} (H-13) hacen
 * que se rechace todo el cuerpo, sin aplicar la parte válida. Lo que no pertenece a
 * {@code AtributosUsuario} (rol, estado, mfaHabilitado, correo…) no llega al servicio.
 */
final class AtributosPropiosWebMapper {

    /** Atributos de AtributosUsuario que el titular no edita en H3. */
    private static final Set<String> PRIVILEGIADOS = Set.of(
            "tipoDocumento", "numeroDocumento", "fechaNacimiento",
            "codigoVendedor", "tienda", "fechaIngreso");

    private AtributosPropiosWebMapper() {
    }

    static CambioAtributos aCambio(Map<String, Object> cuerpo) {
        for (String clave : cuerpo.keySet()) {
            if (PRIVILEGIADOS.contains(clave)) {
                throw new EdicionNoPermitidaException();
            }
        }
        return new CambioAtributos(
                texto("nombres", cuerpo),
                texto("apellidos", cuerpo),
                texto("celular", cuerpo));
    }

    private static CampoEditable<String> texto(String campo, Map<String, Object> cuerpo) {
        if (!cuerpo.containsKey(campo)) {
            return CampoEditable.omitido();
        }
        Object valor = cuerpo.get(campo);
        if (valor == null) {
            return CampoEditable.presente(null);
        }
        if (valor instanceof String) {
            return CampoEditable.presente((String) valor);
        }
        throw new AtributoInvalidoException(campo, "debe ser texto");
    }
}
