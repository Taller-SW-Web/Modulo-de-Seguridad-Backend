package com.marketplace.auth_service.usuario.adapter.in.web.dto;

import java.util.List;

/** Cuerpo de {@code POST /usuarios/lote} (SPEC-18 RF-18.2): hasta 100 identificadores. */
public record LoteUsuariosRequest(List<String> ids) {
}
