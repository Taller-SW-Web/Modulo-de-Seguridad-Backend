package com.marketplace.auth_service.proteccion.application.port.out;
import com.marketplace.auth_service.proteccion.application.dto.PruebaEnlace;
public interface HashEnlaceProteccionPort { String calcular(PruebaEnlace prueba); }
