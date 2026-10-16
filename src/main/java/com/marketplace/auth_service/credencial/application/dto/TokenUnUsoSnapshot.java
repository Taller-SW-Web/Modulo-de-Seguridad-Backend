package com.marketplace.auth_service.credencial.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Resultado interno sin hash persistido ni token claro. */
public record TokenUnUsoSnapshot(UUID id, UUID usuarioId, PropositoToken proposito, UUID contextoId,
                                 Instant expiraEn, Instant creadoEn, boolean usado) { }
