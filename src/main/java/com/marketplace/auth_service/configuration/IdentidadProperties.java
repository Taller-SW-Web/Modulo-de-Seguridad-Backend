package com.marketplace.auth_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de firma (TEC-IDENTIDAD-T001). Todo llega del entorno; no hay
 * valores por defecto que permitan firmar.
 *
 * @param issuer             URL absoluta del emisor, idéntica a la del descubrimiento ({@code AUTH_ISSUER})
 * @param clavePrivada       texto PEM PKCS8 de la clave RSA ({@code AUTH_CLAVE_PRIVADA})
 * @param clavePrivadaArchivo ruta a un archivo PEM PKCS8, alternativa a la anterior ({@code AUTH_CLAVE_PRIVADA_ARCHIVO})
 */
@ConfigurationProperties("identidad")
public record IdentidadProperties(String issuer, String clavePrivada, String clavePrivadaArchivo) {

    @Override
    public String toString() {
        return "IdentidadProperties[issuer=" + issuer + ", clavePrivada=" + (vacio(clavePrivada) ? "no" : "[REDACTADA]")
                + ", clavePrivadaArchivo=" + clavePrivadaArchivo + "]";
    }

    static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
