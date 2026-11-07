package com.marketplace.auth_service.usuario.application.dto;

/**
 * Resultado de la baja y la reactivación (SPEC-04, FEAT-02). {@code HECHO} también cubre los casos
 * sin cambio (reactivar una cuenta que no está {@code INACTIVO}, RF-04.5): la respuesta es la misma.
 */
public enum ResultadoAdministracionCuenta { HECHO, VALIDACION, NO_ENCONTRADO, ADMINISTRADOR_PROTEGIDO, SIN_PERMISO }
