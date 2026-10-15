package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.credencial.adapter.in.transaction.CredencialesTransaccional;
import com.marketplace.auth_service.credencial.adapter.out.capacidad.DatosPersonalesPasswordAdapter;
import com.marketplace.auth_service.credencial.adapter.out.config.PasswordComunesArchivo;
import com.marketplace.auth_service.credencial.adapter.out.crypto.SpringPasswordHasherAdapter;
import com.marketplace.auth_service.credencial.application.port.in.*;
import com.marketplace.auth_service.credencial.application.port.out.*;
import com.marketplace.auth_service.credencial.application.service.*;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.nio.file.Path;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class CredencialesConfig {
    @Bean PasswordHasherPort passwordHasherPort() { return new SpringPasswordHasherAdapter(); }

    @Bean PasswordComunesArchivo passwordComunesArchivo(
            @Value("${g7.password.comunes.archivo:/run/config/g7/password-comunes.txt}") String archivo) {
        return new PasswordComunesArchivo(Path.of(archivo));
    }
    @Bean RecargaComunes recargaComunes(PasswordComunesArchivo archivo) { return new RecargaComunes(archivo); }
    public static final class RecargaComunes {
        private final PasswordComunesArchivo archivo;
        RecargaComunes(PasswordComunesArchivo archivo) { this.archivo = archivo; }
        @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
        public void recargar() { archivo.recargar(); }
    }
    @Bean DatosPersonalesPasswordPort datosPersonalesPasswordPort(CuentasPort cuentas) {
        return new DatosPersonalesPasswordAdapter(cuentas);
    }
    @Bean PoliticaPasswordService politicaPasswordService(PasswordComunesArchivo comunes,
            DatosPersonalesPasswordPort personas, PersistenciaCredencialesPort persistencia, PasswordHasherPort hasher) {
        return new PoliticaPasswordService(comunes, personas, persistencia, new PasswordHistoryChecker(hasher));
    }
    @Bean CredencialesPort credencialesPort(PersistenciaCredencialesPort persistencia, PasswordHasherPort hasher,
            PoliticaPasswordService politica) {
        // Material sintético distinto por arranque. Nunca es una cuenta ni se persiste.
        var equivalente = hasher.generar(UUID.randomUUID().toString());
        return new CredencialesTransaccional(new CredencialesService(persistencia, hasher, equivalente, politica));
    }
}
