package com.marketplace.auth_service.usuario.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;

/** Esquema {@code LoteUsuarios} del contrato (SPEC-18 ESC-18.2). */
public record LoteUsuariosResponse(List<UsuarioServicioResponse> usuarios, List<UUID> noEncontrados) {
}
