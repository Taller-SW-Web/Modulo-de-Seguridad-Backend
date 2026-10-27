package com.marketplace.auth_service.auditoria.domain.model;

/**
 * Catálogo cerrado de acciones auditables (SPEC-12, RF-12.2).
 * Una spec que necesite auditar algo nuevo añade su código aquí mediante
 * acuerdo con el PO; ninguna implementación inventa códigos.
 * La marca crítica indica que la operación revierte si no se pudo auditar (RF-12.6).
 */
public enum AccionAuditable {
    SESION_INICIADA(false),
    SESION_FALLIDA(false),
    SESION_CERRADA(false),
    REFRESCO_REUTILIZADO(false),
    OTP_SOLICITADO(false),
    OTP_VERIFICADO(false),
    OTP_FALLIDO(false),
    MFA_ACTIVADO(true),
    MFA_DESACTIVADO(true),
    USUARIO_CREADO(true),
    USUARIO_VERIFICADO(false),
    CUENTA_ACTIVADA(true),
    BUSQUEDA_POR_DOCUMENTO(true),
    CUENTA_SIN_ACTIVAR_ELIMINADA(false),
    USUARIO_DESACTIVADO(true),
    USUARIO_REACTIVADO(true),
    CONTRASENA_CAMBIADA(true),
    CONTRASENA_RESTABLECIDA(true),
    RECUPERACION_SOLICITADA(false),
    ROL_ASIGNADO(true),
    ROL_REVOCADO(true),
    CUENTA_BLOQUEADA(true),
    CUENTA_DESBLOQUEADA(true),
    ATRIBUTOS_ACTUALIZADOS(false),
    CORREO_CAMBIADO(true),
    MODULO_CONSULTO_USUARIO(false),
    MODULO_OBTUVO_DOCUMENTO(true),
    MODULO_CONSULTO_DIRECCIONES(false),
    ACCESO_DENEGADO(false),
    AUDITORIA_CONSULTADA(false),
    AUDITORIA_EXPORTADA(false),
    AUDITORIA_PURGADA(false);

    private final boolean critica;

    AccionAuditable(boolean critica) {
        this.critica = critica;
    }

    /** Si es crítica, la operación revierte cuando el registro falla (RF-12.6). */
    public boolean esCritica() {
        return critica;
    }
}
