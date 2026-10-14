package com.marketplace.auth_service.usuario.application.dto;

public record ConsultaCuentasQuery(
    FiltroCuenta filtro,
    Paginacion paginacion
) {}