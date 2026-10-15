package com.marketplace.auth_service.perfil.application.dto;

import java.time.LocalDate;

/** Presente solo si la cuenta tiene rol VENDEDOR. Cada campo null = aún sin registrar. */
public record AtributosVendedor(String codigoVendedor, String tienda, LocalDate fechaIngreso) {}
