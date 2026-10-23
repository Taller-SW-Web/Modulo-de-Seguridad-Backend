package com.marketplace.auth_service.usuario.application.dto;

/**
 * Resultado de {@code POST /auth/verificar-correo} (SPEC-02). {@code INVALIDA} (token desconocido)
 * se responde igual que {@code YA_USADA}: el contrato solo publica los dos 410 de enlace.
 */
public enum ResultadoVerificacion { VERIFICADA, EXPIRADA, YA_USADA, INVALIDA }
