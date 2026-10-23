package com.marketplace.auth_service.usuario.adapter.in.web.dto;

import java.util.List;

/** Esquema {@code PaginaUsuarios}: {@code total} son las coincidencias del filtro, no las de la página. */
public record PaginaUsuariosResponse(List<UsuarioResponse> usuarios, int pagina, int tamano, long total) {
}
