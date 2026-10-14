package com.marketplace.auth_service.usuario.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PaginaUsuariosResponse(
    @JsonProperty("usuarios") List<UsuarioResponse> usuarios,
    @JsonProperty("pagina") int pagina,
    @JsonProperty("tamano") int tamano,
    @JsonProperty("total") long total
) {}