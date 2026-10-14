package com.marketplace.auth_service.usuario.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ListarUsuariosRequest(
    @Pattern(regexp = "^(ACTIVO|INACTIVO|BLOQUEADO|PENDIENTE_VERIFICACION)$", message = "Estado invalido")
    String estado,
    @Pattern(regexp = "^(CLIENTE|VENDEDOR|ADMIN_VENTAS|GESTOR_DESPACHO|GESTOR_COMERCIAL|ADMIN_SISTEMA)$", message = "Rol invalido")
    String rol,
    @Size(max = 254) String correo,
    @Min(1) @Max(10000) Integer pagina,
    @Min(1) @Max(100) Integer tamano
) {}