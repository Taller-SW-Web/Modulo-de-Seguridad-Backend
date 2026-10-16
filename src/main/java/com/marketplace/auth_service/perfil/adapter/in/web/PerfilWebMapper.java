package com.marketplace.auth_service.perfil.adapter.in.web;

import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aplicación → esquema {@code PerfilUsuario}. Los atributos de rol aparecen solo
 * si el rol aplica, y entonces van aunque sean {@code null} ("aún sin registrar"),
 * como en el ejemplo del contrato ({@code fechaNacimiento: null}). Por eso es un
 * mapa ordenado y no un record con una sola política de nulos.
 */
final class PerfilWebMapper {

    private PerfilWebMapper() {
    }

    static Map<String, Object> aPerfilUsuario(PerfilProyectado p) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", p.id());
        json.put("nombres", p.nombres());
        json.put("apellidos", p.apellidos());
        json.put("nombreCompleto", p.nombreCompleto());
        json.put("correo", p.correo());
        if (p.celular() != null) {
            json.put("celular", p.celular());
        }
        json.put("estado", p.estado());
        json.put("correoVerificado", p.correoVerificado());
        json.put("celularVerificado", p.celularVerificado());
        json.put("roles", p.roles());
        json.put("mfaHabilitado", p.mfaHabilitado());
        if (p.atributosCliente() != null) {
            json.put("fechaNacimiento", texto(p.atributosCliente().fechaNacimiento()));
        }
        if (p.atributosVendedor() != null) {
            json.put("codigoVendedor", p.atributosVendedor().codigoVendedor());
            json.put("tienda", p.atributosVendedor().tienda());
            json.put("fechaIngreso", texto(p.atributosVendedor().fechaIngreso()));
        }
        if (p.documento() != null) {
            json.put("tipoDocumento", p.documento().tipo());
            json.put("documentoEnmascarado", p.documento().enmascarado());
        }
        return json;
    }

    private static String texto(Object fecha) {
        return fecha == null ? null : fecha.toString();
    }
}
