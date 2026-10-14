package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.identidad.adapter.out.crypto.ClaveFirmaRsa;
import com.marketplace.auth_service.identidad.adapter.out.crypto.ClaveFirmaRsa.ClaveInvalidaException;
import com.marketplace.auth_service.identidad.adapter.out.crypto.NimbusFirmadorJws;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.service.FirmaDenegada;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

/**
 * Wiring de la firma de accesos. Carga y valida la clave y el issuer al
 * arrancar. Si algo falta o es inválido, la aplicación arranca igual pero
 * {@link FirmaPort} deniega toda emisión ({@link FirmaDenegada}): nunca se
 * genera un par efímero (ENTORNO-Y-REACT §2).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentidadProperties.class)
public class IdentidadConfig {

    private static final Logger log = LoggerFactory.getLogger(IdentidadConfig.class);

    @Bean
    FirmaPort firmaPort(IdentidadProperties propiedades, ObjectProvider<Clock> clock) {
        Optional<String> problemaIssuer = problemaIssuer(propiedades.issuer());
        if (problemaIssuer.isPresent()) {
            return denegar(problemaIssuer.get());
        }
        ClaveFirmaRsa clave;
        try {
            clave = cargarClave(propiedades);
        } catch (ClaveInvalidaException e) {
            return denegar(e.getMessage());
        }
        log.info("identidad: firma RS256 habilitada; issuer={}, kid={}", propiedades.issuer(), clave.kid());
        return new FirmarAccesoService(new NimbusFirmadorJws(clave), propiedades.issuer(),
                clock.getIfAvailable(Clock::systemUTC));
    }

    private static FirmaPort denegar(String motivo) {
        log.error("identidad: emisión de accesos DENEGADA: {}", motivo);
        return new FirmaDenegada(motivo);
    }

    static ClaveFirmaRsa cargarClave(IdentidadProperties propiedades) {
        boolean hayTexto = !IdentidadProperties.vacio(propiedades.clavePrivada());
        boolean hayArchivo = !IdentidadProperties.vacio(propiedades.clavePrivadaArchivo());
        if (hayTexto && hayArchivo) {
            throw new ClaveInvalidaException(
                    "configurar solo una de identidad.clave-privada o identidad.clave-privada-archivo");
        }
        if (hayArchivo) {
            Path ruta = Path.of(propiedades.clavePrivadaArchivo());
            try {
                return ClaveFirmaRsa.desdePem(Files.readString(ruta, StandardCharsets.US_ASCII));
            } catch (IOException e) {
                throw new ClaveInvalidaException("no se pudo leer el archivo de clave " + ruta);
            }
        }
        if (hayTexto) {
            return ClaveFirmaRsa.desdePem(propiedades.clavePrivada());
        }
        throw new ClaveInvalidaException("no hay clave privada configurada (AUTH_CLAVE_PRIVADA o AUTH_CLAVE_PRIVADA_ARCHIVO)");
    }

    /** RF-17.14: el issuer es una URL absoluta del entorno, nunca el nombre interno del servicio. */
    static Optional<String> problemaIssuer(String issuer) {
        if (IdentidadProperties.vacio(issuer)) {
            return Optional.of("no hay issuer configurado (AUTH_ISSUER)");
        }
        try {
            URI uri = new URI(issuer);
            boolean http = "https".equals(uri.getScheme()) || "http".equals(uri.getScheme());
            if (!http || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null) {
                return Optional.of("el issuer debe ser una URL http(s) absoluta, sin query ni fragmento");
            }
            if (issuer.endsWith("/")) {
                return Optional.of("el issuer no debe terminar en '/': debe coincidir carácter a carácter con iss");
            }
            if ("auth-service".equalsIgnoreCase(uri.getHost())) {
                return Optional.of("el issuer no puede usar el nombre interno auth-service");
            }
        } catch (URISyntaxException e) {
            return Optional.of("el issuer no es una URL válida");
        }
        return Optional.empty();
    }
}
