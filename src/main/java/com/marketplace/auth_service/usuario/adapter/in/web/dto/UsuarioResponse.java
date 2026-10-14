package com.marketplace.auth_service.usuario.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.UUID;

public record UsuarioResponse(
    @JsonProperty("id") UUID id,
    @JsonProperty("nombres") String nombres,
    @JsonProperty("apellidos") String apellidos,
    @JsonProperty("nombreCompleto") String nombreCompleto,
    @JsonProperty("correo") String correo,
    @JsonProperty("celular") String celular,
    @JsonProperty("estado") String estado,
    @JsonProperty("correoVerificado") boolean correoVerificado,
    @JsonProperty("celularVerificado") boolean celularVerificado,
    @JsonProperty("mfaHabilitado") boolean mfaHabilitado,
    @JsonProperty("fechaNacimiento") String fechaNacimiento,
    @JsonProperty("codigoVendedor") String codigoVendedor,
    @JsonProperty("tienda") String tienda,
    @JsonProperty("fechaIngreso") String fechaIngreso,
    @JsonProperty("roles") Set<String> roles,
    @JsonProperty("tipoDocumento") String tipoDocumento,
    @JsonProperty("documento") String documento,
    @JsonProperty("documentoEnmascarado") String documentoEnmascarado,
    @JsonProperty("bloqueo") BloqueoResponse bloqueo
) {}