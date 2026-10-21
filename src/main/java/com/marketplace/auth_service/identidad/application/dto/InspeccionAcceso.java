package com.marketplace.auth_service.identidad.application.dto;

/**
 * Resultado criptográfico de inspeccionar un acceso de persona para la
 * introspección (SPEC-17 RF-17.6): distingue el vencido del inválido, porque el
 * contrato responde un motivo distinto para cada uno.
 */
public sealed interface InspeccionAcceso {

    /** Firma, issuer, vigencia y perfil de persona correctos. */
    record Valido(AccesoValidado acceso) implements InspeccionAcceso { }

    /** Bien firmado por nosotros, pero su {@code exp} ya pasó. */
    record Vencido() implements InspeccionAcceso { }

    /** Mal firmado, de otro emisor, malformado o que no es un acceso de persona. */
    record Invalido() implements InspeccionAcceso { }
}
