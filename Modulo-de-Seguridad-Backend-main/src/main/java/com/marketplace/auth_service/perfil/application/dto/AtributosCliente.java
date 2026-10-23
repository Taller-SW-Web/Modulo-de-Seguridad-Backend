package com.marketplace.auth_service.perfil.application.dto;

import java.time.LocalDate;

/** Presente solo si la cuenta tiene rol CLIENTE. fechaNacimiento null = aún sin registrar. */
public record AtributosCliente(LocalDate fechaNacimiento) {}
