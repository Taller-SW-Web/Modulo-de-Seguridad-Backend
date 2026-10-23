package com.marketplace.auth_service.credencial.application.port.out;

public interface PasswordComunesPort {
    /** Falla técnicamente cuando no existe una primera lista válida. */
    boolean contiene(String copiaNormalizada);
}
