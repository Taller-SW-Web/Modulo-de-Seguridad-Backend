package com.marketplace.auth_service.perfil.application.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Cambio de atributos hecho por quien tiene {@code atributos.editar} (SPEC-16 RF-16.8, FEAT-04-T6).
 * Lleva los básicos y los atributos de rol; el documento solo se marca como presente porque
 * escribirlo exige la clave AES externa, que aún no existe.
 */
public record CambioAdministrativo(
        CampoEditable<String> nombres,
        CampoEditable<String> apellidos,
        CampoEditable<String> celular,
        CampoEditable<LocalDate> fechaNacimiento,
        CampoEditable<String> codigoVendedor,
        CampoEditable<String> tienda,
        CampoEditable<LocalDate> fechaIngreso,
        boolean documentoPresente) {

    public CambioAdministrativo {
        Objects.requireNonNull(nombres, "nombres");
        Objects.requireNonNull(apellidos, "apellidos");
        Objects.requireNonNull(celular, "celular");
        Objects.requireNonNull(fechaNacimiento, "fechaNacimiento");
        Objects.requireNonNull(codigoVendedor, "codigoVendedor");
        Objects.requireNonNull(tienda, "tienda");
        Objects.requireNonNull(fechaIngreso, "fechaIngreso");
    }

    public CambioAtributos basicos() {
        return new CambioAtributos(nombres, apellidos, celular);
    }

    public boolean tocaCliente() {
        return fechaNacimiento.presente();
    }

    public boolean tocaVendedor() {
        return codigoVendedor.presente() || tienda.presente() || fechaIngreso.presente();
    }

    public boolean hayCambios() {
        return basicos().hayCambios() || tocaCliente() || tocaVendedor();
    }

    /** Nombres de los campos presentes, en orden fijo. Nunca los valores. */
    public List<String> camposPresentes() {
        List<String> campos = new ArrayList<>();
        if (nombres.presente()) campos.add("nombres");
        if (apellidos.presente()) campos.add("apellidos");
        if (celular.presente()) campos.add("celular");
        if (fechaNacimiento.presente()) campos.add("fechaNacimiento");
        if (codigoVendedor.presente()) campos.add("codigoVendedor");
        if (tienda.presente()) campos.add("tienda");
        if (fechaIngreso.presente()) campos.add("fechaIngreso");
        return List.copyOf(campos);
    }
}
