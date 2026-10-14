package com.marketplace.auth_service.usuario.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BloqueoResponse(
    @JsonProperty("tipo") String tipo,
    @JsonProperty("hasta") String hasta,
    @JsonProperty("motivo") String motivo
) {}