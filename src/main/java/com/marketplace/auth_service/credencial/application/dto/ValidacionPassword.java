package com.marketplace.auth_service.credencial.application.dto;

import java.util.List;

public record ValidacionPassword(List<InfraccionPassword> errores) {
    public ValidacionPassword { errores = List.copyOf(errores); }
    public boolean valida() { return errores.isEmpty(); }
}
