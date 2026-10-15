package com.marketplace.auth_service.auditoria.domain.model;

/** Quién realizó la acción: persona, módulo consumidor o tarea interna. */
public enum ActorTipo {
    USUARIO,
    MODULO,
    SISTEMA
}
