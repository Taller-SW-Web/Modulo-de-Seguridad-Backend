package com.marketplace.auth_service.usuario.application.dto;

/**
 * Parámetros de {@code GET /usuarios} tal como llegan en la URL (operación
 * {@code listarUsuarios}). Se validan en el caso de uso <b>después</b> de
 * autorizar, para que alguien sin {@code usuario.ver} reciba 403 y no aprenda
 * nada de la validación.
 *
 * @param estado  {@code EstadoCuenta} efectivo, o null
 * @param rol     {@code CodigoRol}, o null
 * @param correo  fragmento del correo, sin distinguir mayúsculas, o null
 * @param pagina  entero ≥ 1; null es 1
 * @param tamano  entero entre 1 y 100; null es 20
 */
public record ConsultaUsuariosQuery(String estado, String rol, String correo, String pagina, String tamano) {

    public static ConsultaUsuariosQuery sinFiltros() {
        return new ConsultaUsuariosQuery(null, null, null, null, null);
    }
}
