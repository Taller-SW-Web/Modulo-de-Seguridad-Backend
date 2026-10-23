package com.marketplace.auth_service.identidad.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code IntrospeccionActiva} o {@code IntrospeccionInactiva} del contrato: los
 * campos que no aplican a cada rama se omiten, nunca se envían como null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntrospeccionResponse(boolean activo, String motivo, String sub, String email,
                                    List<String> roles, List<String> permisos, String tipo, Long exp) {

    public static IntrospeccionResponse inactiva(String motivo) {
        return new IntrospeccionResponse(false, motivo, null, null, null, null, null, null);
    }
}
