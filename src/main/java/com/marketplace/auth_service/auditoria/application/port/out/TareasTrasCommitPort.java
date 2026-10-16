package com.marketplace.auth_service.auditoria.application.port.out;

/**
 * Ejecuta una tarea después del commit del productor, en su propia transacción, sin
 * afectar al hecho ya confirmado si falla (registro no crítico, SPEC-12 RF-12.6). Sin
 * transacción activa, la ejecuta enseguida en una propia.
 */
public interface TareasTrasCommitPort {
    void trasCommit(Runnable tarea);
}
