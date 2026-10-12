package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.service.FirmaDenegada;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;
import com.nimbusds.jose.JWSObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wiring de la firma con Spring, sin base de datos: configuración válida firma;
 * clave o issuer ausentes o inválidos dejan la emisión denegada.
 */
class IdentidadConfigTest {

    private static final String ISSUER = "https://g7.example.test/api/v1/auth";
    private static final String PEM = ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072);
    private static final ClaimsAcceso CLAIMS =
            new ClaimsAcceso(UUID.randomUUID(), "c@ejemplo.com", List.of("CLIENTE"), List.of());

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(IdentidadConfig.class);

    @Test
    void conClaveEIssuerFirmaAccesos() {
        contexto.withPropertyValues("identidad.issuer=" + ISSUER, "identidad.clave-privada=" + PEM)
                .run(ctx -> {
                    FirmaPort firma = ctx.getBean(FirmaPort.class);
                    assertThat(firma).isInstanceOf(FirmarAccesoService.class);
                    AccesoFirmado acceso = firma.firmar(CLAIMS);
                    assertThat(JWSObject.parse(acceso.token()).getPayload().toJSONObject())
                            .containsEntry("iss", ISSUER);
                });
    }

    @Test
    void laClavePuedeVenirDeUnArchivoYElKidSobreviveAlReinicio(@TempDir Path dir) throws Exception {
        Path archivo = dir.resolve("firma.pem");
        Files.writeString(archivo, PEM);
        String[] kids = new String[2];

        for (int arranque = 0; arranque < 2; arranque++) {
            int i = arranque;
            contexto.withPropertyValues("identidad.issuer=" + ISSUER,
                            "identidad.clave-privada-archivo=" + archivo)
                    .run(ctx -> kids[i] = JWSObject.parse(ctx.getBean(FirmaPort.class).firmar(CLAIMS).token())
                            .getHeader().getKeyID());
        }

        assertThat(kids[0]).isNotNull().isEqualTo(kids[1]);
    }

    @Test
    void sinClaveLaEmisionSeDeniegaPeroLaAplicacionArranca() {
        contexto.withPropertyValues("identidad.issuer=" + ISSUER).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            FirmaPort firma = ctx.getBean(FirmaPort.class);
            assertThat(firma).isInstanceOf(FirmaDenegada.class);
            assertThatThrownBy(() -> firma.firmar(CLAIMS))
                    .isInstanceOf(FirmaNoDisponibleException.class)
                    .hasMessageContaining("AUTH_CLAVE_PRIVADA");
        });
    }

    @Test
    void sinIssuerLaEmisionSeDeniega() {
        contexto.withPropertyValues("identidad.clave-privada=" + PEM).run(ctx ->
                assertThatThrownBy(() -> ctx.getBean(FirmaPort.class).firmar(CLAIMS))
                        .isInstanceOf(FirmaNoDisponibleException.class)
                        .hasMessageContaining("AUTH_ISSUER"));
    }

    @Test
    void unaClaveInvalidaDeniegaLaEmision() {
        String pem2048 = ClavesDePrueba.pemPkcs8(ClavesDePrueba.generar(2048));

        contexto.withPropertyValues("identidad.issuer=" + ISSUER, "identidad.clave-privada=" + pem2048)
                .run(ctx -> assertThat(ctx.getBean(FirmaPort.class)).isInstanceOf(FirmaDenegada.class));
    }

    @Test
    void dosFuentesDeClaveALaVezSeRechazan(@TempDir Path dir) throws Exception {
        Path archivo = dir.resolve("firma.pem");
        Files.writeString(archivo, PEM);

        contexto.withPropertyValues("identidad.issuer=" + ISSUER, "identidad.clave-privada=" + PEM,
                        "identidad.clave-privada-archivo=" + archivo)
                .run(ctx -> assertThat(ctx.getBean(FirmaPort.class)).isInstanceOf(FirmaDenegada.class));
    }

    @Test
    void unArchivoInexistenteDeniegaLaEmision(@TempDir Path dir) {
        contexto.withPropertyValues("identidad.issuer=" + ISSUER,
                        "identidad.clave-privada-archivo=" + dir.resolve("no-existe.pem"))
                .run(ctx -> assertThat(ctx.getBean(FirmaPort.class)).isInstanceOf(FirmaDenegada.class));
    }

    @Test
    void elIssuerDebeSerUnaUrlAbsolutaDelEntorno() {
        assertThat(IdentidadConfig.problemaIssuer(ISSUER)).isEmpty();
        assertThat(IdentidadConfig.problemaIssuer("http://localhost:8080/api/v1/auth")).isEmpty();
        assertThat(IdentidadConfig.problemaIssuer("auth-service")).isPresent();
        assertThat(IdentidadConfig.problemaIssuer("http://auth-service:8080/api/v1/auth")).isPresent();
        assertThat(IdentidadConfig.problemaIssuer(ISSUER + "/")).isPresent();
        assertThat(IdentidadConfig.problemaIssuer("ftp://g7.example.test")).isPresent();
        assertThat(IdentidadConfig.problemaIssuer("")).isPresent();
    }
}
