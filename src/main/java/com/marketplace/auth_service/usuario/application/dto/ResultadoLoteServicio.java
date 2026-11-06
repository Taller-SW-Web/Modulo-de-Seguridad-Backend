package com.marketplace.auth_service.usuario.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de {@code POST /usuarios/lote} para un módulo consumidor (SPEC-18
 * RF-18.2, FEAT-06-T2): esquema {@code LoteUsuarios} del contrato. La petición
 * no falla entera por los identificadores que no existen (ESC-18.2).
 */
public record ResultadoLoteServicio(List<UsuarioServicioVista> usuarios, List<UUID> noEncontrados) {
}
